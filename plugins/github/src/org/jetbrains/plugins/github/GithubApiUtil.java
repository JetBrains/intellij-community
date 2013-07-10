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
  private static final Logger LOG = GithubUtil.LOG;

  private enum HttpVerb {
    GET, POST, DELETE, HEAD
  }

  @Nullable
  public static JsonElement getRequest(@NotNull String host, @NotNull String login, @NotNull String password,
                                       @NotNull String path) throws IOException {
    return request(GithubAuthData.createBasicAuth(host, login, password), path, null, HttpVerb.GET);
  }

  @Nullable
  public static JsonElement getRequest(@NotNull GithubAuthData auth, @NotNull String path) throws IOException {
    return request(auth, path, null, HttpVerb.GET);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull String path, @Nullable String requestBody) throws IOException {
    return request(GithubAuthData.createAnonymous(GithubUrlUtil.getApiUrl()), path, requestBody, HttpVerb.POST);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull GithubAuthData auth, @NotNull String path, @Nullable String requestBody)
    throws IOException {
    return request(auth, path, requestBody, HttpVerb.POST);
  }

  @Nullable
  public static JsonElement deleteRequest(@NotNull GithubAuthData auth, @NotNull String path) throws IOException {
    return request(auth, path, null, HttpVerb.DELETE);
  }

  @Nullable
  private static JsonElement request(@NotNull GithubAuthData auth,
                                     @NotNull String path,
                                     @Nullable String requestBody, @NotNull HttpVerb verb) throws IOException {
    HttpMethod method = null;
    try {
      method = doREST(auth, path, requestBody, verb);

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
        String value = header.getValue();
        int end = value.indexOf(">; rel=\"next\"");
        int begin = value.lastIndexOf('<', end);
        if (begin >= 0 && end >= 0) {
          String newPath = GithubUrlUtil.removeProtocolPrefix(value.substring(begin + 1, end));
          int index = newPath.indexOf('/');
          JsonElement next = request(auth, newPath.substring(index), requestBody, verb);
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
  private static HttpMethod doREST(@NotNull final GithubAuthData auth, @NotNull String path, @Nullable final String requestBody,
                                   @NotNull final HttpVerb verb) throws IOException {
    HttpClient client = getHttpClient(auth.getBasicAuth());
    String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + path;
    return GithubSslSupport.getInstance().executeSelfSignedCertificateAwareRequest(client, uri,
     new ThrowableConvertor<String, HttpMethod, IOException>() {
       @Override
       public HttpMethod convert(String uri) throws IOException {
         HttpMethod method;
         switch (verb) {
           case POST:
             method = new PostMethod(uri);
             if (requestBody != null) {
               ((PostMethod)method).setRequestEntity(new StringRequestEntity(requestBody, "application/json", "UTF-8"));
             }
             break;
           case GET:
             method = new GetMethod(uri);
             break;
           case DELETE:
             method = new DeleteMethod(uri);
             break;
           case HEAD:
             method = new HeadMethod(uri);
             break;
           default:
             throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
         }
         GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
         if (tokenAuth != null) {
           method.addRequestHeader("Authorization", "token " + tokenAuth.getToken());
         }
         return method;
       }
     });
  }

  @NotNull
  private static HttpClient getHttpClient(@Nullable GithubAuthData.BasicAuth basicAuth) {
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
    if (basicAuth != null) {
      client.getParams().setCredentialCharset("UTF-8");
      client.getParams().setAuthenticationPreemptive(true);
      client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(basicAuth.getLogin(), basicAuth.getPassword()));
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
