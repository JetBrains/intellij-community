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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.*;
import org.jetbrains.plugins.github.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GithubApiUtil {

  public static final String DEFAULT_GITHUB_HOST = "github.com";

  private static final String PER_PAGE = "per_page=100";
  private static final Logger LOG = GithubUtil.LOG;

  private static final Header ACCEPT_V3_JSON_HTML_MARKUP = new Header("Accept", "application/vnd.github.v3.html+json");
  private static final Header ACCEPT_V3_JSON = new Header("Accept", "application/vnd.github.v3+json");

  @NotNull private static final Gson gson = initGson();

  private static Gson initGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
    return builder.create();
  }

  private enum HttpVerb {
    GET, POST, DELETE, HEAD
  }

  @Nullable
  private static JsonElement postRequest(@NotNull GithubAuthData auth,
                                         @NotNull String path,
                                         @Nullable String requestBody,
                                         @NotNull Header... headers) throws IOException {
    return request(auth, path, requestBody, Arrays.asList(headers), HttpVerb.POST).getJsonElement();
  }

  @Nullable
  private static JsonElement deleteRequest(@NotNull GithubAuthData auth, @NotNull String path, @NotNull Header... headers)
    throws IOException {
    return request(auth, path, null, Arrays.asList(headers), HttpVerb.DELETE).getJsonElement();
  }

  @Nullable
  private static JsonElement getRequest(@NotNull GithubAuthData auth, @NotNull String path, @NotNull Header... headers) throws IOException {
    return request(auth, path, null, Arrays.asList(headers), HttpVerb.GET).getJsonElement();
  }

  @NotNull
  private static ResponsePage request(@NotNull GithubAuthData auth,
                                      @NotNull String path,
                                      @Nullable String requestBody,
                                      @NotNull Collection<Header> headers,
                                      @NotNull HttpVerb verb) throws IOException {
    HttpMethod method = null;
    try {
      String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + path;
      method = doREST(auth, uri, requestBody, headers, verb);

      checkStatusCode(method);

      InputStream resp = method.getResponseBodyAsStream();
      if (resp == null) {
        return new ResponsePage();
      }

      JsonElement ret = parseResponse(resp);
      if (ret.isJsonNull()) {
        return new ResponsePage();
      }

      Header header = method.getResponseHeader("Link");
      if (header != null) {
        String value = header.getValue();
        int end = value.indexOf(">; rel=\"next\"");
        int begin = value.lastIndexOf('<', end);
        if (begin >= 0 && end >= 0) {
          String newPath = GithubUrlUtil.removeProtocolPrefix(value.substring(begin + 1, end));
          int index = newPath.indexOf('/');

          return new ResponsePage(ret, newPath.substring(index));
        }
      }

      return new ResponsePage(ret);
    }
    finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
  }

  @NotNull
  private static HttpMethod doREST(@NotNull final GithubAuthData auth,
                                   @NotNull final String uri,
                                   @Nullable final String requestBody,
                                   @NotNull final Collection<Header> headers,
                                   @NotNull final HttpVerb verb) throws IOException {
    HttpClient client = getHttpClient(auth.getBasicAuth(), auth.isUseProxy());
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
          for (Header header : headers) {
            method.addRequestHeader(header);
          }
          return method;
        }
      });
  }

  @NotNull
  private static HttpClient getHttpClient(@Nullable GithubAuthData.BasicAuth basicAuth, boolean useProxy) {
    int timeout = GithubSettings.getInstance().getConnectionTimeout();
    final HttpClient client = new HttpClient();
    HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
    params.setConnectionTimeout(timeout); //set connection timeout (how long it takes to connect to remote host)
    params.setSoTimeout(timeout); //set socket timeout (how long it takes to retrieve data from remote host)

    client.getParams().setContentCharset("UTF-8");
    // Configure proxySettings if it is required
    final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    if (useProxy && proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
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

  private static void checkStatusCode(@NotNull HttpMethod method) throws IOException {
    int code = method.getStatusCode();
    switch (code) {
      case HttpStatus.SC_OK:
      case HttpStatus.SC_CREATED:
      case HttpStatus.SC_ACCEPTED:
      case HttpStatus.SC_NO_CONTENT:
        return;
      case HttpStatus.SC_BAD_REQUEST:
      case HttpStatus.SC_UNAUTHORIZED:
      case HttpStatus.SC_PAYMENT_REQUIRED:
      case HttpStatus.SC_FORBIDDEN:
        String message = getErrorMessage(method);
        if (message.contains("API rate limit exceeded")) {
          throw new GithubRateLimitExceededException(message);
        }
        throw new GithubAuthenticationException("Request response: " + message);
      default:
        throw new GithubStatusCodeException(code + ": " + getErrorMessage(method), code);
    }
  }

  @NotNull
  private static String getErrorMessage(@NotNull HttpMethod method) {
    try {
      InputStream resp = method.getResponseBodyAsStream();
      if (resp != null) {
        GithubErrorMessageRaw error = fromJson(parseResponse(resp), GithubErrorMessageRaw.class);
        return method.getStatusText() + " - " + error.getMessage();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return method.getStatusText();
  }

  @NotNull
  private static JsonElement parseResponse(@NotNull InputStream githubResponse) throws IOException {
    Reader reader = new InputStreamReader(githubResponse, "UTF-8");
    try {
      return new JsonParser().parse(reader);
    }
    catch (JsonSyntaxException jse) {
      throw new GithubJsonException("Couldn't parse GitHub response", jse);
    }
    finally {
      reader.close();
    }
  }

  private static class ResponsePage {
    @Nullable private final JsonElement response;
    @Nullable private final String nextPage;

    public ResponsePage() {
      this(null, null);
    }

    public ResponsePage(@Nullable JsonElement response) {
      this(response, null);
    }

    public ResponsePage(@Nullable JsonElement response, @Nullable String next) {
      this.response = response;
      this.nextPage = next;
    }

    @Nullable
    public JsonElement getJsonElement() {
      return response;
    }

    @Nullable
    public String getNextPage() {
      return nextPage;
    }
  }

   /*
   * Json API
   */

  static <Raw extends DataConstructor, Result> Result createDataFromRaw(@NotNull Raw rawObject, @NotNull Class<Result> resultClass)
    throws GithubJsonException {
    try {
      return rawObject.create(resultClass);
    }
    catch (Exception e) {
      throw new GithubJsonException("Json parse error", e);
    }
  }

  public static class PagedRequest<T> {
    @Nullable private String myNextPage;
    @NotNull private final Collection<Header> myHeaders;
    @NotNull private final Class<T> myResult;
    @NotNull private final Class<? extends DataConstructor[]> myRawArray;

    @SuppressWarnings("NullableProblems")
    public PagedRequest(@NotNull String path,
                        @NotNull Class<T> result,
                        @NotNull Class<? extends DataConstructor[]> rawArray,
                        @NotNull Header... headers) {
      myNextPage = path;
      myResult = result;
      myRawArray = rawArray;
      myHeaders = Arrays.asList(headers);
    }

    @NotNull
    public List<T> next(@NotNull GithubAuthData auth) throws IOException {
      if (myNextPage == null) {
        throw new NoSuchElementException();
      }

      String page = myNextPage;
      myNextPage = null;

      ResponsePage response = request(auth, page, null, myHeaders, HttpVerb.GET);

      if (response.getJsonElement() == null) {
        throw new HttpException("Empty response");
      }

      if (!response.getJsonElement().isJsonArray()) {
        throw new GithubJsonException("Wrong json type: expected JsonArray", new Exception(response.getJsonElement().toString()));
      }

      myNextPage = response.getNextPage();

      List<T> result = new ArrayList<T>();
      for (DataConstructor raw : fromJson(response.getJsonElement().getAsJsonArray(), myRawArray)) {
        result.add(createDataFromRaw(raw, myResult));
      }
      return result;
    }

    public boolean hasNext() {
      return myNextPage != null;
    }

    @NotNull
    public List<T> getAll(@NotNull GithubAuthData auth) throws IOException {
      List<T> result = new ArrayList<T>();
      while (hasNext()) {
        result.addAll(next(auth));
      }
      return result;
    }
  }

  @NotNull
  private static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
    if (json == null) {
      throw new GithubJsonException("Unexpected empty response");
    }

    T res;
    try {
      //cast as workaround for early java 1.6 bug
      //noinspection RedundantCast
      res = (T)gson.fromJson(json, classT);
    }
    catch (ClassCastException e) {
      throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
    }
    catch (JsonParseException e) {
      throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
    }
    if (res == null) {
      throw new GithubJsonException("Empty Json response");
    }
    return res;
  }

   /*
   * Github API
   */

  @NotNull
  public static Collection<String> getTokenScopes(@NotNull GithubAuthData auth) throws IOException {
    HttpMethod method = null;
    try {
      String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + "/user";
      method = doREST(auth, uri, null, Collections.<Header>emptyList(), HttpVerb.HEAD);

      checkStatusCode(method);

      Header header = method.getResponseHeader("X-OAuth-Scopes");
      if (header == null) {
        throw new HttpException("No scopes header");
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

  @NotNull
  public static String getScopedToken(@NotNull GithubAuthData auth, @NotNull Collection<String> scopes, @Nullable String note)
    throws IOException {
    try {
      String path = "/authorizations";

      GithubAuthorizationRequest request = new GithubAuthorizationRequest(new ArrayList<String>(scopes), note, null);
      GithubAuthorization response =
        createDataFromRaw(fromJson(postRequest(auth, path, gson.toJson(request)), GithubAuthorizationRaw.class), GithubAuthorization.class);

      return response.getToken();
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get token: scopes - " + scopes);
      throw e;
    }
  }

  @NotNull
  public static String getReadOnlyToken(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, @Nullable String note)
    throws IOException {
    GithubRepo repository = getDetailedRepoInfo(auth, user, repo);

    List<String> scopes = repository.isPrivate() ? Collections.singletonList("repo") : Collections.<String>emptyList();

    return getScopedToken(auth, scopes, note);
  }

  @NotNull
  public static GithubUser getCurrentUser(@NotNull GithubAuthData auth) throws IOException {
    try {
      JsonElement result = getRequest(auth, "/user", ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUser.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info");
      throw e;
    }
  }

  @NotNull
  public static GithubUserDetailed getCurrentUserDetailed(@NotNull GithubAuthData auth) throws IOException {
    try {
      JsonElement result = getRequest(auth, "/user", ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUserDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubAuthData auth) throws IOException {
    try {
      String path = "/user/repos?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubAuthData auth, @NotNull String user) throws IOException {
    try {
      String path = "/users/" + user + "/repos?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories: " + user);
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getAvailableRepos(@NotNull GithubAuthData auth) throws IOException {
    try {
      List<GithubRepo> repos = new ArrayList<GithubRepo>();

      repos.addAll(getUserRepos(auth));
      repos.addAll(getMembershipRepos(auth));
      repos.addAll(getWatchedRepos(auth));

      return repos;
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get available repositories");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepoOrg> getMembershipRepos(@NotNull GithubAuthData auth) throws IOException {
    String orgsPath = "/user/orgs?" + PER_PAGE;
    PagedRequest<GithubOrg> orgsRequest = new PagedRequest<GithubOrg>(orgsPath, GithubOrg.class, GithubOrgRaw[].class);

    List<GithubRepoOrg> repos = new ArrayList<GithubRepoOrg>();
    for (GithubOrg org : orgsRequest.getAll(auth)) {
      String path = "/orgs/" + org.getLogin() + "/repos?type=member&" + PER_PAGE;
      PagedRequest<GithubRepoOrg> request =
        new PagedRequest<GithubRepoOrg>(path, GithubRepoOrg.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);
      repos.addAll(request.getAll(auth));
    }

    return repos;
  }

  @NotNull
  public static List<GithubRepo> getWatchedRepos(@NotNull GithubAuthData auth) throws IOException {
    String pathWatched = "/user/subscriptions?" + PER_PAGE;
    PagedRequest<GithubRepo> requestWatched =
      new PagedRequest<GithubRepo>(pathWatched, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);
    return requestWatched.getAll(auth);
  }

  @NotNull
  public static GithubRepoDetailed getDetailedRepoInfo(@NotNull GithubAuthData auth, @NotNull String owner, @NotNull String name)
    throws IOException {
    try {
      final String request = "/repos/" + owner + "/" + name;

      JsonElement jsonObject = getRequest(auth, request, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(jsonObject, GithubRepoRaw.class), GithubRepoDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get repository info: " + owner + "/" + name);
      throw e;
    }
  }

  public static void deleteGithubRepository(@NotNull GithubAuthData auth, @NotNull String username, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + username + "/" + repo;
      deleteRequest(auth, path);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't delete repository: " + username + "/" + repo);
      throw e;
    }
  }

  public static void deleteGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    try {
      String path = "/gists/" + id;
      deleteRequest(auth, path);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't delete gist: id - " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubGist getGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    try {
      String path = "/gists/" + id;
      JsonElement result = getRequest(auth, path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubGistRaw.class), GithubGist.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get gist info: id " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubGist createGist(@NotNull GithubAuthData auth,
                                      @NotNull List<GithubGist.FileContent> contents,
                                      @NotNull String description,
                                      boolean isPrivate) throws IOException {
    try {
      String request = gson.toJson(new GithubGistRequest(contents, description, !isPrivate));
      return createDataFromRaw(fromJson(postRequest(auth, "/gists", request, ACCEPT_V3_JSON), GithubGistRaw.class), GithubGist.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create gist");
      throw e;
    }
  }

  @NotNull
  public static GithubPullRequest createPullRequest(@NotNull GithubAuthData auth,
                                                    @NotNull String user,
                                                    @NotNull String repo,
                                                    @NotNull String title,
                                                    @NotNull String description,
                                                    @NotNull String head,
                                                    @NotNull String base) throws IOException {
    try {
      String request = gson.toJson(new GithubPullRequestRequest(title, description, head, base));
      return createDataFromRaw(
        fromJson(postRequest(auth, "/repos/" + user + "/" + repo + "/pulls", request, ACCEPT_V3_JSON), GithubPullRequestRaw.class),
        GithubPullRequest.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create pull request");
      throw e;
    }
  }

  @NotNull
  public static GithubRepo createRepo(@NotNull GithubAuthData auth, @NotNull String name, @NotNull String description, boolean isPrivate)
    throws IOException {
    try {
      String path = "/user/repos";

      GithubRepoRequest request = new GithubRepoRequest(name, description, isPrivate);

      return createDataFromRaw(fromJson(postRequest(auth, path, gson.toJson(request), ACCEPT_V3_JSON), GithubRepoRaw.class),
                               GithubRepo.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create repository: " + name);
      throw e;
    }
  }

  /*
   * Open issues only
   */
  @NotNull
  public static List<GithubIssue> getIssuesAssigned(@NotNull GithubAuthData auth,
                                                    @NotNull String user,
                                                    @NotNull String repo,
                                                    @Nullable String assigned,
                                                    int max) throws IOException {
    try {
      String path;
      if (StringUtil.isEmptyOrSpaces(assigned)) {
        path = "/repos/" + user + "/" + repo + "/issues?" + PER_PAGE;
      }
      else {
        path = "/repos/" + user + "/" + repo + "/issues?assignee=" + assigned + "&" + PER_PAGE;
      }

      PagedRequest<GithubIssue> request = new PagedRequest<GithubIssue>(path, GithubIssue.class, GithubIssueRaw[].class, ACCEPT_V3_JSON);

      List<GithubIssue> result = new ArrayList<GithubIssue>();
      while (request.hasNext() && max > result.size()) {
        result.addAll(request.next(auth));
      }
      return result;
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get assigned issues: " + user + "/" + repo + " - " + assigned);
      throw e;
    }
  }

  @NotNull
  /*
   * All issues - open and closed
   */
  public static List<GithubIssue> getIssuesQueried(@NotNull GithubAuthData auth,
                                                   @NotNull String user,
                                                   @NotNull String repo,
                                                   @Nullable String query) throws IOException {
    try {
      query = URLEncoder.encode("repo:" + user + "/" + repo + " " + query, "UTF-8");
      String path = "/search/issues?q=" + query;

      //TODO: Use bodyHtml for issues - GitHub does not support this feature for SearchApi yet
      JsonElement result = getRequest(auth, path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubIssuesSearchResultRaw.class), GithubIssuesSearchResult.class).getIssues();
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get queried issues: " + user + "/" + repo + " - " + query);
      throw e;
    }
  }

  @NotNull
  public static GithubIssue getIssue(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, @NotNull String id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id;

      JsonElement result = getRequest(auth, path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubIssueRaw.class), GithubIssue.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get issue info: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubIssueComment> getIssueComments(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id + "/comments?" + PER_PAGE;

      PagedRequest<GithubIssueComment> request =
        new PagedRequest<GithubIssueComment>(path, GithubIssueComment.class, GithubIssueCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get issue comments: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubCommitDetailed getCommit(@NotNull GithubAuthData auth,
                                               @NotNull String user,
                                               @NotNull String repo,
                                               @NotNull String sha) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/commits/" + sha;

      JsonElement result = getRequest(auth, path, ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubCommitRaw.class), GithubCommitDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get commit info: " + user + "/" + repo + " - " + sha);
      throw e;
    }
  }

  @NotNull
  public static List<GithubCommitComment> getCommitComments(@NotNull GithubAuthData auth,
                                                            @NotNull String user,
                                                            @NotNull String repo,
                                                            @NotNull String sha) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/commits/" + sha + "/comments";

      PagedRequest<GithubCommitComment> request =
        new PagedRequest<GithubCommitComment>(path, GithubCommitComment.class, GithubCommitCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get commit comments: " + user + "/" + repo + " - " + sha);
      throw e;
    }
  }

  @NotNull
  public static List<GithubCommitComment> getPullRequestComments(@NotNull GithubAuthData auth,
                                                                 @NotNull String user,
                                                                 @NotNull String repo,
                                                                 long id) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/comments";

      PagedRequest<GithubCommitComment> request =
        new PagedRequest<GithubCommitComment>(path, GithubCommitComment.class, GithubCommitCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request comments: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubPullRequest getPullRequest(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, int id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id;
      return createDataFromRaw(fromJson(getRequest(auth, path, ACCEPT_V3_JSON_HTML_MARKUP), GithubPullRequestRaw.class),
                               GithubPullRequest.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request info: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubPullRequest> getPullRequests(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls?" + PER_PAGE;

      PagedRequest<GithubPullRequest> request =
        new PagedRequest<GithubPullRequest>(path, GithubPullRequest.class, GithubPullRequestRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull requests" + user + "/" + repo);
      throw e;
    }
  }

  @NotNull
  public static PagedRequest<GithubPullRequest> getPullRequests(@NotNull String user, @NotNull String repo) {
    String path = "/repos/" + user + "/" + repo + "/pulls?" + PER_PAGE;

    return new PagedRequest<GithubPullRequest>(path, GithubPullRequest.class, GithubPullRequestRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);
  }

  @NotNull
  public static List<GithubCommit> getPullRequestCommits(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/commits?" + PER_PAGE;

      PagedRequest<GithubCommit> request =
        new PagedRequest<GithubCommit>(path, GithubCommit.class, GithubCommitRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request commits: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubFile> getPullRequestFiles(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo, long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/files?" + PER_PAGE;

      PagedRequest<GithubFile> request = new PagedRequest<GithubFile>(path, GithubFile.class, GithubFileRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request files: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubBranch> getRepoBranches(@NotNull GithubAuthData auth, @NotNull String user, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/branches?" + PER_PAGE;

      PagedRequest<GithubBranch> request =
        new PagedRequest<GithubBranch>(path, GithubBranch.class, GithubBranchRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(auth);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get repository branches: " + user + "/" + repo);
      throw e;
    }
  }

  @Nullable
  public static GithubRepo findForkByUser(@NotNull GithubAuthData auth,
                                          @NotNull String user,
                                          @NotNull String repo,
                                          @NotNull String forkUser) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/forks?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      while (request.hasNext()) {
        for (GithubRepo fork : request.next(auth)) {
          if (StringUtil.equalsIgnoreCase(fork.getUserName(), forkUser)) {
            return fork;
          }
        }
      }

      return null;
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't find fork by user: " + user + "/" + repo + " - " + forkUser);
      throw e;
    }
  }
}