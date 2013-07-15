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
package org.jetbrains.plugins.github.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubSslSupport;
import org.jetbrains.plugins.github.GithubUrlUtil;
import org.jetbrains.plugins.github.GithubUtil;
import org.jetbrains.plugins.github.api.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GithubApiUtil {

  public static final String DEFAULT_GITHUB_HOST = "github.com";

  private static final int CONNECTION_TIMEOUT = 5000;
  private static final Logger LOG = GithubUtil.LOG;

  @NotNull public static final Gson gson = initGson();

  private static Gson initGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
    return builder.create();
  }

  private enum HttpVerb {
    GET, POST, DELETE, HEAD
  }

  @Nullable
  public static JsonElement getRequest(@NotNull GithubAuthData auth, @NotNull String path) throws IOException {
    return request(auth, path, null, HttpVerb.GET);
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
                                     @Nullable String requestBody,
                                     @NotNull HttpVerb verb) throws IOException {
    HttpMethod method = null;
    try {
      method = doREST(auth, path, requestBody, verb);

      String resp = method.getResponseBodyAsString();
      if (resp == null) {
        LOG.info("Unexpectedly empty response");
        return null;
      }

      checkStatusCode(method);

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
  private static HttpMethod doREST(@NotNull final GithubAuthData auth,
                                   @NotNull String path,
                                   @Nullable final String requestBody,
                                   @NotNull final HttpVerb verb) throws IOException {
    HttpClient client = getHttpClient(auth.getBasicAuth());
    String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + path;
    return GithubSslSupport.getInstance()
      .executeSelfSignedCertificateAwareRequest(client, uri, new ThrowableConvertor<String, HttpMethod, IOException>() {
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
    if (proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
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

  private static void checkStatusCode(@NotNull HttpMethod method) throws AuthenticationException {
    switch (method.getStatusCode()) {
      case 400: // HTTP_BAD_REQUEST
      case 401: // HTTP_UNAUTHORIZED
      case 402: // HTTP_PAYMENT_REQUIRED
      case 403: // HTTP_FORBIDDEN
      case 404: // HTTP_NOT_FOUND
        throw new AuthenticationException("Request response: " + method.getStatusText());
    }
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

  /*
   * Github API
   */

  @Nullable
  public static Collection<String> getTokenScopes(@NotNull GithubAuthData auth) throws IOException {
    HttpMethod method = null;
    try {
      method = doREST(auth, "", null, HttpVerb.HEAD);

      checkStatusCode(method);

      Header header = method.getResponseHeader("X-OAuth-Scopes");
      if (header == null) {
        return null;
      }

      Collection<String> scopes = new ArrayList<String>();
      for (HeaderElement elem : header.getElements()) {
        scopes.add(elem.getName());
      }
      return scopes;
    }
    finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
  }

  @Nullable
  public static String getScopedToken(@NotNull GithubAuthData auth, @NotNull Collection<String> scopes, @Nullable String note)
    throws IOException {
    String path = "/authorizations";

    JsonObject request = new JsonObject();
    JsonArray json = new JsonArray();
    for (String scope : scopes) {
      json.add(new JsonPrimitive(scope));
    }
    request.add("scopes", json);
    request.addProperty("note", note != null ? note : "Intellij GitHub plugin");

    JsonElement result = postRequest(auth, path, request.toString());

    if (result == null || !result.isJsonObject()) {
      return null;
    }
    JsonElement token = result.getAsJsonObject().get("token");
    if (token == null) {
      return null;
    }

    return token.getAsString();
  }

  @Nullable
  public static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
    return fromJson(json, (Type)classT);
  }

  @Nullable
  public static <T> T fromJson(@Nullable JsonElement json, @NotNull Type type) throws IOException {
    if (json == null) {
      return null;
    }

    T res;
    try {
      res = gson.fromJson(json, type);
    }
    catch (JsonParseException jpe) {
      IOException ioe = new IOException("Parse exception converting JSON to object " + type.toString());
      ioe.initCause(jpe);
      throw ioe;
    }
    return res;
  }

  @Nullable
  public static GithubUserDetailed getCurrentUserInfo(@NotNull GithubAuthData auth) throws IOException {
    JsonElement result = getRequest(auth, "/user");
    return GithubUserDetailed.createDetailed(fromJson(result, GithubUserRaw.class));
  }

  @NotNull
  public static List<GithubRepo> getAvailableRepos(@NotNull GithubAuthData auth) throws IOException {
    return doGetAvailableRepos(auth, null);
  }

  @NotNull
  public static List<GithubRepo> getAvailableRepos(@NotNull GithubAuthData auth, @NotNull String user) throws IOException {
    return doGetAvailableRepos(auth, user);
  }

  @NotNull
  private static List<GithubRepo> doGetAvailableRepos(@NotNull GithubAuthData auth, @Nullable String user) throws IOException {
    String request = user == null ? "/user/repos" : "/users/" + user + "/repos";
    JsonElement result = getRequest(auth, request);
    List<GithubRepoRaw> rawRepos = fromJson(result, new TypeToken<List<GithubRepoRaw>>() {
    }.getType());
    if (rawRepos == null) {
      return Collections.emptyList();
    }
    List<GithubRepo> repos = new ArrayList<GithubRepo>();
    for (GithubRepoRaw raw : rawRepos) {
      GithubRepo repo = GithubRepo.create(raw);
      if (repo != null) {
        repos.add(repo);
      }
    }
    return repos;
  }

  @Nullable
  public static GithubRepoDetailed getDetailedRepoInfo(@NotNull GithubAuthData auth, @NotNull String owner, @NotNull String name)
    throws IOException {
    final String request = "/repos/" + owner + "/" + name;

    JsonElement jsonObject = getRequest(auth, request);

    return GithubRepoDetailed.createDetailed(fromJson(jsonObject, GithubRepoRaw.class));
  }

  public static void deleteGithubRepository(@NotNull GithubAuthData auth, @NotNull String username, @NotNull String repo)
    throws IOException {
    String path = "/repos/" + username + "/" + repo;
    deleteRequest(auth, path);
  }

  public static void deleteGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    String path = "/gists/" + id;
    deleteRequest(auth, path);
  }

  @Nullable
  public static GithubGistRaw getGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    String path = "/gists/" + id;
    JsonElement result = getRequest(auth, path);

    return fromJson(result, GithubGistRaw.class);
  }

  @Nullable
  public static GithubGist createGist(@NotNull GithubAuthData auth,
                                      @NotNull Map<String, String> contents,
                                      @NotNull String description,
                                      boolean isPrivate) throws IOException {
    String request = gson.toJson(new GithubGistRequest(contents, description, !isPrivate));
    return GithubGist.create(fromJson(postRequest(auth, "/gists", request), GithubGistRaw.class));
  }
}
