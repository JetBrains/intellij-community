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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.NullCheckingFactory;
import org.jetbrains.plugins.github.api.GithubConnection.ArrayPagedRequest;
import org.jetbrains.plugins.github.api.GithubConnection.PagedRequest;
import org.jetbrains.plugins.github.api.data.*;
import org.jetbrains.plugins.github.api.requests.*;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
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
    builder.registerTypeAdapterFactory(NullCheckingFactory.INSTANCE);
    return builder.create();
  }

  @NotNull
  public static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
    if (json == null) {
      throw new GithubJsonException("Unexpected empty response");
    }

    try {
      T res = gson.fromJson(json, classT);
      if (res == null) throw new GithubJsonException("Empty Json response");
      return res;
    }
    catch (ClassCastException | JsonParseException e) {
      throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
    }
  }

  @NotNull
  private static <T> List<T> loadAll(@NotNull GithubConnection connection,
                                     @NotNull String path,
                                     @NotNull Class<? extends T[]> type,
                                     @NotNull Header... headers) throws IOException {
    PagedRequest<T> request = new ArrayPagedRequest<>(path, type, headers);
    return request.getAll(connection);
  }

  @NotNull
  private static <T> T load(@NotNull GithubConnection connection,
                            @NotNull String path,
                            @NotNull Class<? extends T> type,
                            @NotNull Header... headers) throws IOException {
    JsonElement result = connection.getRequest(path, headers);
    return fromJson(result, type);
  }

  @NotNull
  private static <T> T post(@NotNull GithubConnection connection,
                            @NotNull String path,
                            @NotNull Object request,
                            @NotNull Class<? extends T> type,
                            @NotNull Header... headers) throws IOException {
    JsonElement result = connection.postRequest(path, gson.toJson(request), headers);
    return fromJson(result, type);
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

    Collection<String> scopes = new ArrayList<>();
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
          if (!ContainerUtil.exists(tokens, authorization -> newNote.equals(authorization.getNote()))) {
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

      GithubAuthorizationUpdateRequest request = new GithubAuthorizationUpdateRequest(new ArrayList<>(scopes));

      return fromJson(connection.patchRequest(path, gson.toJson(request), ACCEPT_V3_JSON), GithubAuthorization.class);
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

      GithubAuthorizationCreateRequest request = new GithubAuthorizationCreateRequest(new ArrayList<>(scopes), note, null);
      return post(connection, path, request, GithubAuthorization.class, ACCEPT_V3_JSON);
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
      return loadAll(connection, path, GithubAuthorization[].class, ACCEPT_V3_JSON);
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
  public static GithubAuthenticatedUser getCurrentUser(@NotNull GithubConnection connection) throws IOException {
    try {
      return load(connection, "/user", GithubAuthenticatedUser.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info");
      throw e;
    }
  }

  @NotNull
  public static GithubUserDetailed getUser(@NotNull GithubConnection connection, @NotNull String username) throws IOException {
    try {
      return load(connection, "/users/" + username, GithubUserDetailed.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user info for: " + username);
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubConnection connection) throws IOException {
    return getUserRepos(connection, false);
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubConnection connection, boolean allAssociated) throws IOException {
    try {
      String type = allAssociated ? "" : "type=owner&";
      String path = "/user/repos?" + type + PER_PAGE;
      return loadAll(connection, path, GithubRepo[].class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getUserRepos(@NotNull GithubConnection connection, @NotNull String user) throws IOException {
    try {
      String path = "/users/" + user + "/repos?type=owner&" + PER_PAGE;
      return loadAll(connection, path, GithubRepo[].class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get user repositories: " + user);
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getAvailableRepos(@NotNull GithubConnection connection) throws IOException {
    try {
      List<GithubRepo> repos = new ArrayList<>(getUserRepos(connection, true));

      // We already can return something useful from getUserRepos, so let's ignore errors.
      // One of this may not exist in GitHub enterprise
      try {
        repos.addAll(getWatchedRepos(connection));
      }
      catch (GithubAuthenticationException | GithubStatusCodeException ignore) {
      }

      return repos;
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get available repositories");
      throw e;
    }
  }

  @NotNull
  private static List<GithubRepo> getWatchedRepos(@NotNull GithubConnection connection) throws IOException {
    String pathWatched = "/user/subscriptions?" + PER_PAGE;
    return loadAll(connection, pathWatched, GithubRepo[].class, ACCEPT_V3_JSON);
  }

  @NotNull
  public static GithubRepoDetailed getDetailedRepoInfo(@NotNull GithubConnection connection, @NotNull String owner, @NotNull String name)
    throws IOException {
    try {
      final String request = "/repos/" + owner + "/" + name;
      return load(connection, request, GithubRepoDetailed.class, ACCEPT_V3_JSON);
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
      return load(connection, path, GithubGist.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get gist info: id " + id);
      throw e;
    }
  }

  @NotNull
  public static GithubGist createGist(@NotNull GithubConnection connection,
                                      @NotNull List<GithubGistRequest.FileContent> contents,
                                      @NotNull String description,
                                      boolean isPublic) throws IOException {
    try {
      GithubGistRequest request = new GithubGistRequest(contents, description, isPublic);
      return post(connection, "/gists", request, GithubGist.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't create gist");
      throw e;
    }
  }

  @NotNull
  public static List<GithubRepo> getForks(@NotNull GithubConnection connection,
                                          @NotNull String owner,
                                          @NotNull String name) throws IOException {
    String path = "/repos/" + owner + "/" + name + "/forks?" + PER_PAGE;
    return loadAll(connection, path, GithubRepo[].class, ACCEPT_V3_JSON);
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
      String path = "/repos/" + user + "/" + repo + "/pulls";
      GithubPullRequestRequest request = new GithubPullRequestRequest(title, description, head, base);
      return post(connection, path, request, GithubPullRequest.class, ACCEPT_V3_JSON);
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
      return post(connection, path, request, GithubRepo.class, ACCEPT_V3_JSON);
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

      PagedRequest<GithubIssue> request = new ArrayPagedRequest<>(path, GithubIssue[].class, ACCEPT_V3_JSON);

      List<GithubIssue> result = new ArrayList<>();
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
      return load(connection, path, GithubIssuesSearchResult.class, ACCEPT_V3_JSON).getIssues();
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
      return load(connection, path, GithubIssue.class, ACCEPT_V3_JSON);
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
      return loadAll(connection, path, GithubIssueComment[].class, ACCEPT_V3_JSON_HTML_MARKUP);
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

      fromJson(result, GithubIssue.class);
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
      return load(connection, path, GithubCommitDetailed.class, ACCEPT_V3_JSON);
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
      return loadAll(connection, path, GithubCommitComment[].class, ACCEPT_V3_JSON_HTML_MARKUP);
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
      return loadAll(connection, path, GithubCommitComment[].class, ACCEPT_V3_JSON_HTML_MARKUP);
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
      return load(connection, path, GithubPullRequest.class, ACCEPT_V3_JSON_HTML_MARKUP);
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
      return loadAll(connection, path, GithubPullRequest[].class, ACCEPT_V3_JSON_HTML_MARKUP);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get pull requests" + user + "/" + repo);
      throw e;
    }
  }

  @NotNull
  public static PagedRequest<GithubPullRequest> getPullRequests(@NotNull String user, @NotNull String repo) {
    String path = "/repos/" + user + "/" + repo + "/pulls?state=all&" + PER_PAGE;
    return new ArrayPagedRequest<>(path, GithubPullRequest[].class, ACCEPT_V3_JSON_HTML_MARKUP);
  }

  @NotNull
  public static List<GithubCommit> getPullRequestCommits(@NotNull GithubConnection connection,
                                                         @NotNull String user,
                                                         @NotNull String repo,
                                                         long id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/commits?" + PER_PAGE;
      return loadAll(connection, path, GithubCommit[].class, ACCEPT_V3_JSON);
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
      return loadAll(connection, path, GithubFile[].class, ACCEPT_V3_JSON);
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
      return loadAll(connection, path, GithubBranch[].class, ACCEPT_V3_JSON);
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

      PagedRequest<GithubRepo> request = new ArrayPagedRequest<>(path, GithubRepo[].class, ACCEPT_V3_JSON);

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
