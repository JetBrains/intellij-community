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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
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

  @Nullable
  public static JsonElement getRequest(@NotNull String host, @NotNull String login, @NotNull String password,
                                       @NotNull String path) throws IOException {
    return request(host, login, password, path, null, false);
  }

  @Nullable
  public static JsonElement postRequest(@NotNull String host, @Nullable String login, @Nullable String password,
                                        @NotNull String path, @Nullable String requestBody) throws IOException {
    return request(host, login, password, path, requestBody, true);
  }

  @Nullable
  private static JsonElement request(@NotNull String host, @Nullable String login, @Nullable String password,
                                     @NotNull String path, @Nullable String requestBody, boolean post) throws IOException {
    HttpMethod method = null;
    try {
      method = doREST(host, login, password, path, requestBody, post);
      String resp = method.getResponseBodyAsString();
      if (resp == null) {
        LOG.info(String.format("Unexpectedly empty response: %s", resp));
        return null;
      }
      return parseResponse(resp);
    }
    finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
  }

  @NotNull
  private static HttpMethod doREST(@NotNull String host, @Nullable String login, @Nullable String password, @NotNull String path,
                                   @Nullable String requestBody, final boolean post) throws IOException {
    final HttpClient client = getHttpClient(login, password);
    final String uri = getApiUrl(host) + path;
    final HttpMethod method;
    if (post) {
      method = new PostMethod(uri);
      if (requestBody != null) {
        ((PostMethod)method).setRequestEntity(new StringRequestEntity(requestBody, "application/json", "UTF-8"));
      }
    }
    else {
      method = new GetMethod(uri);
    }

    client.executeMethod(method);
    return method;
  }

  @NotNull
  private static String removeProtocolPrefix(final String url) {
    if (url.startsWith("https://")) {
      return url.substring(8);
    }
    else if (url.startsWith("http://")) {
        return url.substring(7);
    }
    else if (url.startsWith("git@")) {
      return url.substring(4);
    }
    else {
      return url;
    }
  }

  @NotNull
  private static String getApiUrl(@NotNull String urlFromSettings) {
    return "https://" + getApiUrlWithoutProtocol(urlFromSettings);
  }

  /*
   All API access is over HTTPS, and accessed from the api.github.com domain
   (or through yourdomain.com/api/v3/ for enterprise).
   http://developer.github.com/v3/
  */
  @NotNull
  private static String getApiUrlWithoutProtocol(String urlFromSettings) {
    String url = removeTrailingSlash(removeProtocolPrefix(urlFromSettings));
    final String API_PREFIX = "api.";
    final String ENTERPRISE_API_SUFFIX = "/api/v3";

    if (url.equals(DEFAULT_GITHUB_HOST)) {
      return API_PREFIX + url;
    }
    else if (url.equals(API_PREFIX + DEFAULT_GITHUB_HOST)) {
      return url;
    }
    else if (url.endsWith(ENTERPRISE_API_SUFFIX)) {
      return url;
    }
    else {
      return url + ENTERPRISE_API_SUFFIX;
    }
  }

  private static String removeTrailingSlash(String s) {
    if (s.endsWith("/")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @NotNull
  private static HttpClient getHttpClient(@Nullable final String login, @Nullable final String password) {
    final HttpClient client = new HttpClient();
    client.getParams().setConnectionManagerTimeout(3000);
    client.getParams().setSoTimeout(CONNECTION_TIMEOUT);
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
