/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubConnection.PagedRequest;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

public class GithubApiUtil {
  private static final Logger LOG = GithubUtil.LOG;

  public static final String DEFAULT_GITHUB_HOST = "github.com";

  private static final String PER_PAGE = "per_page=100";

  private static final Header ACCEPT_V3_JSON_HTML_MARKUP = new BasicHeader("Accept", "application/vnd.github.v3.html+json");
  private static final Header ACCEPT_V3_JSON = new BasicHeader("Accept", "application/vnd.github.v3+json");

  @NotNull private static final Gson gson = initGson();

  private static Gson initGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
    return builder.create();
  }

  @NotNull
  public static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
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

  @NotNull
  public static <Raw extends DataConstructor, Result> Result createDataFromRaw(@NotNull Raw rawObject, @NotNull Class<Result> resultClass)
    throws GithubJsonException {
    try {
      return rawObject.create(resultClass);
    }
    catch (Exception e) {
      throw new GithubJsonException("Json parse error", e);
    }
  }

  /*
   * Operations
   */

  public static void askForTwoFactorCodeSMS(@NotNull GithubConnection connection) {
    try {
      connection.postRequest("/authorizations", null, ACCEPT_V3_JSON);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  public static Collection<String> getTokenScopes(@NotNull GithubConnection connection) throws IOException {
    Header[] headers = connection.headRequest("/user", ACCEPT_V3_JSON);

    Header scopesHeader = null;
    for (Header header : headers) {
      if (header.getName().equals("X-OAuth-Scopes")) {
        scopesHeader = header;
        break;
      }
    }
    if (scopesHeader == null) {
      throw new GithubConfusingException("No scopes header");
    }

    Collection<String> scopes = new ArrayList<String>();
    for (HeaderElement elem : scopesHeader.getElements()) {
      scopes.add(elem.getName());
    }
    return scopes;
  }

  @NotNull
  public static String getScopedToken(@NotNull GithubConnection connection, @NotNull Collection<String> scopes, @NotNull String note)
    throws IOException {
    try {
      return getNewScopedToken(connection, scopes, note).getToken();
    }
    catch (GithubStatusCodeException e) {
      if (e.getError() != null && e.getError().containsErrorCode("already_exists")) {
        // with new API we can't reuse old token, so let's just create new one
        // we need to change note as well, because it should be unique

        List<GithubAuthorization> tokens = getAllTokens(connection);

        for (int i = 1; i < 100; i++) {
          final String newNote = note + "_" + i;
          if (ContainerUtil.find(tokens, new Condition<GithubAuthorization>() {
            @Override
            public boolean value(GithubAuthorization authorization) {
              return newNote.equals(authorization.getNote());
            }
          }) == null) {
            return getNewScopedToken(connection, scopes, newNote).getToken();
          }
        }
      }
      throw e;
    }
  }

  @NotNull
  private static GithubAuthorization updateTokenScopes(@NotNull GithubConnection connection,
                                                       @NotNull GithubAuthorization token,
                                                       @NotNull Collection<String> scopes) throws IOException {
    try {
      String path = "/authorizations/" + token.getId();

      GithubAuthorizationUpdateRequest request = new GithubAuthorizationUpdateRequest(new ArrayList<String>(scopes));

      return createDataFromRaw(fromJson(connection.patchRequest(path, gson.toJson(request), ACCEPT_V3_JSON), GithubAuthorizationRaw.class),
                               GithubAuthorization.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't update token: scopes - " + scopes);
      throw e;
    }
  }

  @NotNull
  private static GithubAuthorization getNewScopedToken(@NotNull GithubConnection connection,
                                                       @NotNull Collection<String> scopes,
                                                       @NotNull String note)
    throws IOException {
    try {
      String path = "/authorizations";

      GithubAuthorizationCreateRequest request = new GithubAuthorizationCreateRequest(new ArrayList<String>(scopes), note, null);

      return createDataFromRaw(fromJson(connection.postRequest(path, gson.toJson(request), ACCEPT_V3_JSON), GithubAuthorizationRaw.class),
                               GithubAuthorization.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create token: scopes - " + scopes + " - note " + note);
      throw e;
    }
  }

  @NotNull
  private static List<GithubAuthorization> getAllTokens(@NotNull GithubConnection connection) throws IOException {
    try {
      String path = "/authorizations";

      PagedRequest<GithubAuthorization> request =
        new PagedRequest<GithubAuthorization>(path, GithubAuthorization.class, GithubAuthorizationRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get available tokens");
      throw e;
    }
  }

  @NotNull
  public static String getMasterToken(@NotNull GithubConnection connection, @NotNull String note) throws IOException {
    // "repo" - read/write access to public/private repositories
    // "gist" - create/delete gists
    List<String> scopes = Arrays.asList("repo", "gist");

    return getScopedToken(connection, scopes, note);
  }

  @NotNull
  public static String getTasksToken(@NotNull GithubConnection connection,
                                     @NotNull String user,
                                     @NotNull String repo,
                                     @NotNull String note)
    throws IOException {
    GithubRepo repository = getDetailedRepoInfo(connection, user, repo);

    List<String> scopes = repository.isPrivate() ? Collections.singletonList("repo") : Collections.singletonList("public_repo");

    return getScopedToken(connection, scopes, note);
  }

  @NotNull
  public static GithubUser getCurrentUser(@NotNull GithubConnection connection) throws IOException {
    try {
      JsonElement result = connection.getRequest("/user", ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUser.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info");
      throw e;
    }
  }

  @NotNull
  public static GithubUserDetailed getCurrentUserDetailed(@NotNull GithubConnection connection) throws IOException {
    try {
      JsonElement result = connection.getRequest("/user", ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUserDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubConnection connection) throws IOException {
    try {
      String path = "/user/repos?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubConnection connection, @NotNull String user) throws IOException {
    try {
      String path = "/users/" + user + "/repos?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories: " + user);
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getAvailableRepos(@NotNull GithubConnection connection) throws IOException {
    try {
      List<GithubRepo> repos = new ArrayList<GithubRepo>();

      repos.addAll(getUserRepos(connection));

      // We already can return something useful from getUserRepos, so let's ignore errors.
      // One of this may not exist in GitHub enterprise
      try {
        repos.addAll(getMembershipRepos(connection));
      }
      catch (GithubStatusCodeException ignore) {
      }
      try {
        repos.addAll(getWatchedRepos(connection));
      }
      catch (GithubStatusCodeException ignore) {
      }

      return repos;
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get available repositories");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepoOrg> getMembershipRepos(@NotNull GithubConnection connection) throws IOException {
    String orgsPath = "/user/orgs?" + PER_PAGE;
    PagedRequest<GithubOrg> orgsRequest = new PagedRequest<GithubOrg>(orgsPath, GithubOrg.class, GithubOrgRaw[].class);

    List<GithubRepoOrg> repos = new ArrayList<GithubRepoOrg>();
    for (GithubOrg org : orgsRequest.getAll(connection)) {
      String path = "/orgs/" + org.getLogin() + "/repos?type=member&" + PER_PAGE;
      PagedRequest<GithubRepoOrg> request =
        new PagedRequest<GithubRepoOrg>(path, GithubRepoOrg.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);
      repos.addAll(request.getAll(connection));
    }

    return repos;
  }

  @NotNull
  public static List<GithubRepo> getWatchedRepos(@NotNull GithubConnection connection) throws IOException {
    String pathWatched = "/user/subscriptions?" + PER_PAGE;
    PagedRequest<GithubRepo> requestWatched =
      new PagedRequest<GithubRepo>(pathWatched, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);
    return requestWatched.getAll(connection);
  }

  @NotNull
  public static GithubRepoDetailed getDetailedRepoInfo(@NotNull GithubConnection connection, @NotNull String owner, @NotNull String name)
    throws IOException {
    try {
      final String request = "/repos/" + owner + "/" + name;

      JsonElement jsonObject = connection.getRequest(request, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(jsonObject, GithubRepoRaw.class), GithubRepoDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get repository info: " + owner + "/" + name);
      throw e;
    }
  }

  public static void deleteGithubRepository(@NotNull GithubConnection connection, @NotNull String username, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + username + "/" + repo;
      connection.deleteRequest(path);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't delete repository: " + username + "/" + repo);
      throw e;
    }
  }

  public static void deleteGist(@NotNull GithubConnection connection, @NotNull String id) throws IOException {
    try {
      String path = "/gists/" + id;
      connection.deleteRequest(path);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't delete gist: id - " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubGist getGist(@NotNull GithubConnection connection, @NotNull String id) throws IOException {
    try {
      String path = "/gists/" + id;
      JsonElement result = connection.getRequest(path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubGistRaw.class), GithubGist.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get gist info: id " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubGist createGist(@NotNull GithubConnection connection,
                                      @NotNull List<GithubGist.FileContent> contents,
                                      @NotNull String description,
                                      boolean isPrivate) throws IOException {
    try {
      String request = gson.toJson(new GithubGistRequest(contents, description, !isPrivate));
      return createDataFromRaw(fromJson(connection.postRequest("/gists", request, ACCEPT_V3_JSON), GithubGistRaw.class), GithubGist.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create gist");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getForks(@NotNull GithubConnection connection, @NotNull String owner, @NotNull String name)
    throws IOException {
    String path = "/repos/" + owner + "/" + name + "/forks?" + PER_PAGE;
    PagedRequest<GithubRepo> requestWatched =
      new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);
    return requestWatched.getAll(connection);
  }

  @NotNull
  public static GithubPullRequest createPullRequest(@NotNull GithubConnection connection,
                                                    @NotNull String user,
                                                    @NotNull String repo,
                                                    @NotNull String title,
                                                    @NotNull String description,
                                                    @NotNull String head,
                                                    @NotNull String base) throws IOException {
    try {
      String request = gson.toJson(new GithubPullRequestRequest(title, description, head, base));
      return createDataFromRaw(
        fromJson(connection.postRequest("/repos/" + user + "/" + repo + "/pulls", request, ACCEPT_V3_JSON), GithubPullRequestRaw.class),
        GithubPullRequest.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create pull request");
      throw e;
    }
  }

  @NotNull
  public static GithubRepo createRepo(@NotNull GithubConnection connection,
                                      @NotNull String name,
                                      @NotNull String description,
                                      boolean isPrivate)
    throws IOException {
    try {
      String path = "/user/repos";

      GithubRepoRequest request = new GithubRepoRequest(name, description, isPrivate);

      return createDataFromRaw(fromJson(connection.postRequest(path, gson.toJson(request), ACCEPT_V3_JSON), GithubRepoRaw.class),
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
  public static List<GithubIssue> getIssuesAssigned(@NotNull GithubConnection connection,
                                                    @NotNull String user,
                                                    @NotNull String repo,
                                                    @Nullable String assigned,
                                                    int max,
                                                    boolean withClosed) throws IOException {
    try {
      String state = "state=" + (withClosed ? "all" : "open");
      String path;
      if (StringUtil.isEmptyOrSpaces(assigned)) {
        path = "/repos/" + user + "/" + repo + "/issues?" + PER_PAGE + "&" + state;
      }
      else {
        path = "/repos/" + user + "/" + repo + "/issues?assignee=" + assigned + "&" + PER_PAGE + "&" + state;
      }

      PagedRequest<GithubIssue> request = new PagedRequest<GithubIssue>(path, GithubIssue.class, GithubIssueRaw[].class, ACCEPT_V3_JSON);

      List<GithubIssue> result = new ArrayList<GithubIssue>();
      while (request.hasNext() && max > result.size()) {
        result.addAll(request.next(connection));
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
  public static List<GithubIssue> getIssuesQueried(@NotNull GithubConnection connection,
                                                   @NotNull String user,
                                                   @NotNull String repo,
                                                   @Nullable String assignedUser,
                                                   @Nullable String query,
                                                   boolean withClosed) throws IOException {
    try {
      String state = withClosed ? "" : " state:open";
      String assignee = StringUtil.isEmptyOrSpaces(assignedUser) ? "" : " assignee:" + assignedUser;
      query = URLEncoder.encode("repo:" + user + "/" + repo + state + assignee + " " + query, CharsetToolkit.UTF8);
      String path = "/search/issues?q=" + query;

      //TODO: Use bodyHtml for issues - GitHub does not support this feature for SearchApi yet
      JsonElement result = connection.getRequest(path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubIssuesSearchResultRaw.class), GithubIssuesSearchResult.class).getIssues();
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get queried issues: " + user + "/" + repo + " - " + query);
      throw e;
    }
  }

  @NotNull
  public static GithubIssue getIssue(@NotNull GithubConnection connection, @NotNull String user, @NotNull String repo, @NotNull String id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id;

      JsonElement result = connection.getRequest(path, ACCEPT_V3_JSON);

      return createDataFromRaw(fromJson(result, GithubIssueRaw.class), GithubIssue.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get issue info: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubIssueComment> getIssueComments(@NotNull GithubConnection connection,
                                                          @NotNull String user,
                                                          @NotNull String repo,
                                                          long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id + "/comments?" + PER_PAGE;

      PagedRequest<GithubIssueComment> request =
        new PagedRequest<GithubIssueComment>(path, GithubIssueComment.class, GithubIssueCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get issue comments: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  public static void setIssueState(@NotNull GithubConnection connection,
                                   @NotNull String user,
                                   @NotNull String repo,
                                   @NotNull String id,
                                   boolean open)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id;

      GithubChangeIssueStateRequest request = new GithubChangeIssueStateRequest(open ? "open" : "closed");

      JsonElement result = connection.patchRequest(path, gson.toJson(request), ACCEPT_V3_JSON);

      createDataFromRaw(fromJson(result, GithubIssueRaw.class), GithubIssue.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't set issue state: " + user + "/" + repo + " - " + id + "@" + (open ? "open" : "closed"));
      throw e;
    }
  }


  @NotNull
  public static GithubCommitDetailed getCommit(@NotNull GithubConnection connection,
                                               @NotNull String user,
                                               @NotNull String repo,
                                               @NotNull String sha) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/commits/" + sha;

      JsonElement result = connection.getRequest(path, ACCEPT_V3_JSON);
      return createDataFromRaw(fromJson(result, GithubCommitRaw.class), GithubCommitDetailed.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get commit info: " + user + "/" + repo + " - " + sha);
      throw e;
    }
  }

  @NotNull
  public static List<GithubCommitComment> getCommitComments(@NotNull GithubConnection connection,
                                                            @NotNull String user,
                                                            @NotNull String repo,
                                                            @NotNull String sha) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/commits/" + sha + "/comments";

      PagedRequest<GithubCommitComment> request =
        new PagedRequest<GithubCommitComment>(path, GithubCommitComment.class, GithubCommitCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get commit comments: " + user + "/" + repo + " - " + sha);
      throw e;
    }
  }

  @NotNull
  public static List<GithubCommitComment> getPullRequestComments(@NotNull GithubConnection connection,
                                                                 @NotNull String user,
                                                                 @NotNull String repo,
                                                                 long id) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/comments";

      PagedRequest<GithubCommitComment> request =
        new PagedRequest<GithubCommitComment>(path, GithubCommitComment.class, GithubCommitCommentRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request comments: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubPullRequest getPullRequest(@NotNull GithubConnection connection, @NotNull String user, @NotNull String repo, int id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id;
      return createDataFromRaw(fromJson(connection.getRequest(path, ACCEPT_V3_JSON_HTML_MARKUP), GithubPullRequestRaw.class),
                               GithubPullRequest.class);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request info: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubPullRequest> getPullRequests(@NotNull GithubConnection connection, @NotNull String user, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls?" + PER_PAGE;

      PagedRequest<GithubPullRequest> request =
        new PagedRequest<GithubPullRequest>(path, GithubPullRequest.class, GithubPullRequestRaw[].class, ACCEPT_V3_JSON_HTML_MARKUP);

      return request.getAll(connection);
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
  public static List<GithubCommit> getPullRequestCommits(@NotNull GithubConnection connection,
                                                         @NotNull String user,
                                                         @NotNull String repo,
                                                         long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/commits?" + PER_PAGE;

      PagedRequest<GithubCommit> request =
        new PagedRequest<GithubCommit>(path, GithubCommit.class, GithubCommitRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request commits: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubFile> getPullRequestFiles(@NotNull GithubConnection connection,
                                                     @NotNull String user,
                                                     @NotNull String repo,
                                                     long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/files?" + PER_PAGE;

      PagedRequest<GithubFile> request = new PagedRequest<GithubFile>(path, GithubFile.class, GithubFileRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull request files: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }

  @NotNull
  public static List<GithubBranch> getRepoBranches(@NotNull GithubConnection connection, @NotNull String user, @NotNull String repo)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/branches?" + PER_PAGE;

      PagedRequest<GithubBranch> request =
        new PagedRequest<GithubBranch>(path, GithubBranch.class, GithubBranchRaw[].class, ACCEPT_V3_JSON);

      return request.getAll(connection);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get repository branches: " + user + "/" + repo);
      throw e;
    }
  }

  @Nullable
  public static GithubRepo findForkByUser(@NotNull GithubConnection connection,
                                          @NotNull String user,
                                          @NotNull String repo,
                                          @NotNull String forkUser) throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/forks?" + PER_PAGE;

      PagedRequest<GithubRepo> request = new PagedRequest<GithubRepo>(path, GithubRepo.class, GithubRepoRaw[].class, ACCEPT_V3_JSON);

      while (request.hasNext()) {
        for (GithubRepo fork : request.next(connection)) {
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
