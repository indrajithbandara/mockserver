package org.mockserver.integration.server;

import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.callback.StaticTestExpectationCallback;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;
import org.mockserver.socket.PortFactory;

import javax.script.ScriptEngineManager;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public abstract class SameJVMAbstractClientServerIntegrationTest extends AbstractClientServerIntegrationTest {

    @Test
    public void shouldCallbackToSpecifiedClassWithResponseOnStaticField() {
        // given
        StaticTestExpectationCallback.httpRequests.clear();
        StaticTestExpectationCallback.httpResponse = response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withHeaders(
                        header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response");

        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("callback"))
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.integration.callback.StaticTestExpectationCallback")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore
                )
        );
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldReturnResponseFromJavaScriptTemplate() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path"))
                )
                .respond(
                        template(
                                HttpTemplate.TemplateType.JAVASCRIPT,
                                "return {" + NEW_LINE +
                                        "     'statusCode': 200," + NEW_LINE +
                                        "     'cookies': [ { name: 'name', value: request.cookies['name'] } ]," +
                                        "     'body': JSON.stringify(" + NEW_LINE +
                                        "               {" + NEW_LINE +
                                        "                    method: request.method," +
                                        "                    path: request.path," +
                                        "                    body: request.body" +
                                        "               }" + NEW_LINE +
                                        "          )" + NEW_LINE +
                                        "};" + NEW_LINE
                        )
                );

        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {

            // then
            // - in http
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withCookie("name", "value")
                            .withHeader("set-cookie", "name=value")
                            .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );
            // - in https
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withCookie("name", "value")
                            .withHeader("set-cookie", "name=value")
                            .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                    makeRequest(
                            request()
                                    .withSecure(true)
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );

        } else {

            // then
            // - in http
            assertEquals(
                    notFoundResponse(),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );
            // - in https
            assertEquals(
                    notFoundResponse(),
                    makeRequest(
                            request()
                                    .withSecure(true)
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );

        }
    }

    @Test
    public void shouldForwardTemplateInJavaScript() {
        int testServerHttpsPort = PortFactory.findFreePort();
        EchoServer secureEchoServer = new EchoServer(testServerHttpsPort, false);
        try {
            // when
            mockServerClient
                    .when(
                            request()
                                    .withPath(calculatePath("echo"))
                    )
                    .forward(
                            template(HttpTemplate.TemplateType.JAVASCRIPT,
                                    "return {" + NEW_LINE +
                                            "    'path' : \"/somePath\"," + NEW_LINE +
                                            "    'headers' : [ {" + NEW_LINE +
                                            "        'name' : \"Host\"," + NEW_LINE +
                                            "        'values' : [ \"127.0.0.1:" + testServerHttpsPort + "\" ]" + NEW_LINE +
                                            "    }, {" + NEW_LINE +
                                            "        'name' : \"x-test\"," + NEW_LINE +
                                            "        'values' : [ request.headers['x-test'][0] ]" + NEW_LINE +
                                            "    } ]," + NEW_LINE +
                                            "    'body': \"template_\" + request.body" + NEW_LINE +
                                            "};")
                                    .withDelay(MILLISECONDS, 10)
                    );

            if (new ScriptEngineManager().getEngineByName("nashorn") != null) {

                // then
                // - in http
                assertEquals(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("template_an_example_body_http"),
                        makeRequest(
                                request()
                                        .withPath(calculatePath("echo"))
                                        .withMethod("POST")
                                        .withHeaders(
                                                header("x-test", "test_headers_and_body")
                                        )
                                        .withBody("an_example_body_http"),
                                headersToIgnore)
                );
                // - in https
                assertEquals(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("template_an_example_body_https"),
                        makeRequest(
                                request()
                                        .withSecure(true)
                                        .withPath(calculatePath("echo"))
                                        .withMethod("POST")
                                        .withHeaders(
                                                header("x-test", "test_headers_and_body")
                                        )
                                        .withBody("an_example_body_https"),
                                headersToIgnore)
                );

            } else {

                // then
                // - in http
                assertEquals(
                        notFoundResponse(),
                        makeRequest(
                                request()
                                        .withPath(calculatePath("some_path"))
                                        .withCookie("name", "value")
                                        .withBody("some_request_body"),
                                headersToIgnore)
                );
                // - in https
                assertEquals(
                        notFoundResponse(),
                        makeRequest(
                                request()
                                        .withSecure(true)
                                        .withPath(calculatePath("some_path"))
                                        .withCookie("name", "value")
                                        .withBody("some_request_body"),
                                headersToIgnore)
                );

            }
        } finally {
            secureEchoServer.stop();
        }
    }
}
