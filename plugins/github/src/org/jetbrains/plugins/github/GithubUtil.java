/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Various utility methods for the GutHub plugin.
 *
 * @author oleg
 * @author Kirill Likhodedov
 */
public class GithubUtil {

  public static final Icon GITHUB_ICON = IconLoader.getIcon("/org/jetbrains/plugins/github/github_icon.png");

  private static final Logger LOG = Logger.getInstance(GithubUtil.class.getName());

  /**
   * @deprecated The host may be defined in different formats. Use {@link #getApiUrl(String)} instead.
   */
  @Deprecated
  public static String getHttpsUrl() {
    return "https://" + GithubSettings.getInstance().getHost();
  }

  /**
   * @deprecated TODO Use background progress
   */
  @Deprecated
  public static <T> T accessToGithubWithModalProgress(final Project project, final Computable<T> computable) {
    final Ref<T> result = new Ref<T>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        result.set(computable.compute());
      }
    });
    return result.get();
  }

  /**
   * @deprecated TODO Use background progress
   */
  @Deprecated
  public static void accessToGithubWithModalProgress(final Project project, final Runnable runnable) {
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        runnable.run();
      }
    });
  }

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

    if (url.equals(GithubSettings.DEFAULT_GITHUB_HOST)) {
      return API_PREFIX + url;
    }
    else if (url.equals(API_PREFIX + GithubSettings.DEFAULT_GITHUB_HOST)) {
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
    client.getParams().setContentCharset("UTF-8");
    // Configure proxySettings if it is required
    final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    if (proxySettings.USE_HTTP_PROXY){
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

  private static boolean testConnection(final String url, final String login, final String password) {
    GithubUser user = retrieveCurrentUserInfo(url, login, password);
    return user != null;
  }

  @Nullable
  private static GithubUser retrieveCurrentUserInfo(@NotNull String url, @NotNull String login, @NotNull String password) {
    try {
      JsonElement result = getRequest(url, login, password, "/user");
      return parseUserInfo(result);
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  private static GithubUser parseUserInfo(@Nullable JsonElement result) {
    if (result == null) {
      return null;
    }
    if (!result.isJsonObject()) {
      LOG.error(String.format("Unexpected JSON result format: %s", result));
      return null;
    }

    JsonObject obj = (JsonObject)result;
    if (!obj.has("plan")) {
      return null;
    }
    GithubUser.Plan plan = parsePlan(obj.get("plan"));
    return new GithubUser(plan);
  }

  @NotNull
  private static GithubUser.Plan parsePlan(JsonElement plan) {
    if (!plan.isJsonObject()) {
      return GithubUser.Plan.FREE;
    }
    return GithubUser.Plan.fromString(plan.getAsJsonObject().get("name").getAsString());
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

  @NotNull
  private static List<RepositoryInfo> getAvailableRepos(@NotNull String url, @NotNull String login, @NotNull String password,
                                                       boolean ownOnly) {
    final String request = (ownOnly ? "/user/repos" : "/user/watched");
    try {
      JsonElement result = getRequest(url, login, password, request);
      if (result == null) {
        return Collections.emptyList();
      }
      return parseRepositoryInfos(result);
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private static List<RepositoryInfo> parseRepositoryInfos(@NotNull JsonElement result) {
    if (!result.isJsonArray()) {
      LOG.assertTrue(result.isJsonObject(), String.format("Unexpected JSON result format: %s", result));
      return Collections.singletonList(parseSingleRepositoryInfo(result.getAsJsonObject()));
    }

    List<RepositoryInfo> repositories = new ArrayList<RepositoryInfo>();
    for (JsonElement element : result.getAsJsonArray()) {
      LOG.assertTrue(element.isJsonObject(),
                     String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
      repositories.add(parseSingleRepositoryInfo(element.getAsJsonObject()));
    }
    return repositories;
  }

  @NotNull
  private static RepositoryInfo parseSingleRepositoryInfo(@NotNull JsonObject result) {
    String name = result.get("name").getAsString();
    String cloneUrl = result.get("clone_url").getAsString();
    String ownerName = result.get("owner").getAsJsonObject().get("login").getAsString();
    String parentName = result.has("parent") ? result.get("parent").getAsJsonObject().get("full_name").getAsString(): null;
    boolean fork = result.get("fork").getAsBoolean();
    return new RepositoryInfo(name, cloneUrl, ownerName, parentName, fork);
  }

  @Nullable
  private static RepositoryInfo getDetailedRepoInfo(@NotNull String url, @NotNull String login, @NotNull String password,
                                                   @NotNull String owner, @NotNull String name) {
    try {
      final String request = "/repos/" + owner + "/" + name;
      JsonElement jsonObject = getRequest(url, login, password, request);
      if (jsonObject == null) {
        LOG.info(String.format("Information about repository is unavailable. Owner: %s, Name: %s", owner, name));
        return null;
      }
      return parseSingleRepositoryInfo(jsonObject.getAsJsonObject());
    }
    catch (IOException e) {
      LOG.info(String.format("Exception was thrown when trying to retrieve information about repository.  Owner: %s, Name: %s",
                             owner, name));
      return null;
    }
  }

  public static boolean isPrivateRepoAllowed(final String url, final String login, final String password) {
    GithubUser user = retrieveCurrentUserInfo(url, login, password);
    if (user == null) {
      return false;
    }
    return user.getPlan().isPrivateRepoAllowed();
  }

  public static boolean checkCredentials(final Project project) {
    final GithubSettings settings = GithubSettings.getInstance();
    return checkCredentials(project, settings.getHost(), settings.getLogin(), settings.getPassword());
  }

  public static boolean checkCredentials(final Project project, final String url, final String login, final String password) {
    if (StringUtil.isEmptyOrSpaces(url) || StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)){
      return false;
    }
    return accessToGithubWithModalProgress(project, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
        return testConnection(url, login, password);
      }
    });
  }

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static List<RepositoryInfo> getAvailableRepos(final Project project, final boolean ownOnly) {
    while (!checkCredentials(project)){
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()){
        return null;
      }
    }
    // Otherwise our credentials are valid and they are successfully stored in settings
    final GithubSettings settings = GithubSettings.getInstance();
    final String validPassword = settings.getPassword();
    return accessToGithubWithModalProgress(project, new Computable<List<RepositoryInfo>>() {
      @Override
      public List<RepositoryInfo> compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
        return getAvailableRepos(settings.getHost(), settings.getLogin(), validPassword, ownOnly);
      }
    });
  }

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static RepositoryInfo getDetailedRepositoryInfo(final Project project, final String owner, final String name) {
    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final Boolean validCredentials = accessToGithubWithModalProgress(project, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
        return testConnection(settings.getHost(), settings.getLogin(), password);
      }
    });
    if (validCredentials == null) {
      return null;
    }
    if (!validCredentials){
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()) {
        return null;
      }
    }
    // Otherwise our credentials are valid and they are successfully stored in settings
    final String validPassword = settings.getPassword();
    return accessToGithubWithModalProgress(project, new Computable<RepositoryInfo>() {
      @Nullable
      @Override
      public RepositoryInfo compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Extracting detailed info about repository ''" + name + "''");
        return getDetailedRepoInfo(settings.getHost(), settings.getLogin(), validPassword, owner, name);
      }
    });
  }

  @Nullable
  public static GitRemote findGitHubRemoteBranch(@NotNull GitRepository repository) {
    // i.e. find origin which points on my github repo
    // Check that given repository is properly configured git repository
    for (GitRemote gitRemote : repository.getRemotes()) {
      if (getGithubUrl(gitRemote) != null){
        return gitRemote;
      }
    }
    return null;
  }

  @Nullable
  public static String getGithubUrl(final GitRemote gitRemote){
    final GithubSettings githubSettings = GithubSettings.getInstance();
    final String host = githubSettings.getHost();
    final String username = githubSettings.getLogin();

    final String userRepoMarkerSSHProtocol = host + ":" + username + "/";
    final String userRepoMarkerOtherProtocols = host + "/" + username + "/";
    for (String pushUrl : gitRemote.getUrls()) {
      if (pushUrl.contains(userRepoMarkerSSHProtocol) || pushUrl.contains(userRepoMarkerOtherProtocols)) {
        return pushUrl;
      }
    }
    return null;
  }

  public static boolean testGitExecutable(final Project project) {
    final GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
    final String executable = settings.getPathToGit();
    final GitVersion version;
    try {
      version = GitVersion.identifyVersion(executable);
    } catch (Exception e) {
      Messages.showErrorDialog(project, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return false;
    }

    if (!version.isSupported()) {
      Messages.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                                 GitBundle.getString("find.git.success.title"));
      return false;
    }
    return true;
  }

}
