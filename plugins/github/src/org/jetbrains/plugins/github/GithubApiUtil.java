/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Kirill Likhodedov
 */
public class GithubApiUtil {

  public static final String DEFAULT_GITHUB_HOST = "github.com";

  private static final int CONNECTION_TIMEOUT = 5000;
  private static final Logger LOG = Logger.getInstance(GithubApiUtil.class);

  private enum HttpVerb {
    GET, POST, DELETE, HEAD
  }

  @Nullable
  public static JsonElement getRequest(@NotNull String host, @NotNull String login, @NotNull String password,
                                       @NotNull String path) throws IOException {
    return request(host, login, password, path, null, HttpVerb.GET);
  }

  @Nullable
  public static JsonElement getRequest(@NotNull GithubAuthData auth, @NotNull String path) throws IOException {
    return request(auth.getHost(), auth.getLogin(), auth.getPassword(), path, null, HttpVerb.GET);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull String host, @NotNull String path, @Nullable String requestBody) throws IOException {
    return request(host, null, null, path, requestBody, HttpVerb.POST);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull GithubAuthData auth, @NotNull String path, @Nullable String requestBody)
    throws IOException {
    return request(auth.getHost(), auth.getLogin(), auth.getPassword(), path, requestBody, HttpVerb.POST);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull String host,
                                        @NotNull GithubAuthData auth,
                                        @NotNull String path,
                                        @Nullable String requestBody) throws IOException {
    return request(host, auth.getLogin(), auth.getPassword(), path, requestBody, HttpVerb.POST);
  }

  @Nullable
  public static JsonElement deleteRequest(@NotNull GithubAuthData auth, @NotNull String path) throws IOException {
    return request(auth.getHost(), auth.getLogin(), auth.getPassword(), path, null, HttpVerb.DELETE);
  }

  @Nullable
  private static JsonElement request(@NotNull String host, @Nullable String login, @Nullable String password,
                                     @NotNull String path,
                                     @Nullable String requestBody, @NotNull HttpVerb verb) throws IOException {
    HttpMethod method = null;
    try {
      method = doREST(host, login, password, path, requestBody, verb);
      String resp = method.getResponseBodyAsString();
      if (resp == null) {
        LOG.info(String.format("Unexpectedly empty response: %s", resp));
        return null;
      }
      if (method.getStatusCode() >= 400 && method.getStatusCode() <= 404) {
        throw new AuthenticationException("Request response: " + method.getStatusText());
      }

      JsonElement ret = parseResponse(resp);

      Header header = method.getResponseHeader("Link");
      if (header != null) {
        String s = header.getValue();
        final int end = s.indexOf(">; rel=\"next\"");
        final int begin = s.lastIndexOf('<', end);
        if (begin >= 0 && end >= 0) {
          JsonElement next = request(s.substring(begin + 1, end), login, password, "", requestBody, verb);
          if (next != null) {
            JsonArray merged = ret.getAsJsonArray();
            merged.addAll(next.getAsJsonArray());
            return merged;
          }
        }
      }

      return ret;
    }
    finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
  }

  @NotNull
  private static HttpMethod doREST(@NotNull String host, @Nullable String login, @Nullable String password, @NotNull String path,
                                   @Nullable final String requestBody,
                                   @NotNull final HttpVerb verb) throws IOException {
    HttpClient client = getHttpClient(login, password);
    String uri = GithubUrlUtil.getApiUrl(host) + path;
    return GithubSslSupport.getInstance().executeSelfSignedCertificateAwareRequest(client, uri,
     new ThrowableConvertor<String, HttpMethod, IOException>() {
       @Override
       public HttpMethod convert(String uri) throws IOException {
         switch (verb) {
           case POST:
             PostMethod method = new PostMethod(uri);
             if (requestBody != null) {
               method.setRequestEntity(new StringRequestEntity(requestBody, "application/json", "UTF-8"));
             }
             return method;
           case GET:
             return new GetMethod(uri);
           case DELETE:
             return new DeleteMethod(uri);
           case HEAD:
             return new HeadMethod(uri);
           default:
             throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
         }
       }
     });
  }

  @NotNull
  private static HttpClient getHttpClient(@Nullable final String login, @Nullable final String password) {
    final HttpClient client = new HttpClient();
    HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
    params.setConnectionTimeout(CONNECTION_TIMEOUT); //set connection timeout (how long it takes to connect to remote host)
    params.setSoTimeout(CONNECTION_TIMEOUT); //set socket timeout (how long it takes to retrieve data from remote host)

    client.getParams().setContentCharset("UTF-8");
    // Configure proxySettings if it is required
    final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    if (proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)){
      client.getHostConfiguration().setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
      if (proxySettings.PROXY_AUTHENTICATION) {
        client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                                                                                             proxySettings.getPlainProxyPassword()));
      }
    }
    if (login != null && password != null) {
      client.getParams().setCredentialCharset("UTF-8");
      client.getParams().setAuthenticationPreemptive(true);
      client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(login, password));
    }
    return client;
  }


  @NotNull
  private static JsonElement parseResponse(@NotNull String githubResponse) throws IOException {
    try {
      return new JsonParser().parse(githubResponse);
    }
    catch (JsonSyntaxException jse) {
      throw new IOException(String.format("Couldn't parse GitHub response:%n%s", githubResponse), jse);
    }
  }
}
