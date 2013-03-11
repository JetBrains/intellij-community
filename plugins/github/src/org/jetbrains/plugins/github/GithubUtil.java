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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Various utility methods for the GutHub plugin.
 *
 * @author oleg
 * @author Kirill Likhodedov
 */
public class GithubUtil {

  public static final Logger LOG = Logger.getInstance("github");

  static final String GITHUB_NOTIFICATION_GROUP = "github";

  /**
   * @deprecated The host may be defined in different formats. Use {@link GithubApiUtil#getApiUrl(String)} instead.
   */
  @Deprecated
  public static String getHttpsUrl() {
    return "https://" + GithubSettings.getInstance().getHost();
  }

  @Nullable
  public static <T> T accessToGithubWithModalProgress(@NotNull final Project project, @NotNull String host,
                                                      @NotNull final ThrowableComputable<T, IOException> computable) throws IOException {
    try {
      return doAccessToGithubWithModalProgress(project, computable);
    }
    catch (IOException e) {
      GithubSslSupport sslSupport = GithubSslSupport.getInstance();
      if (GithubSslSupport.isCertificateException(e)) {
        if (sslSupport.askIfShouldProceed(host)) {
          // retry with the host being already trusted
          return doAccessToGithubWithModalProgress(project, computable);
        }
        else {
          return null;
        }
      }
      throw e;
    }
  }

  private static <T> T doAccessToGithubWithModalProgress(@NotNull final Project project,
                                                         @NotNull final ThrowableComputable<T, IOException> computable) throws IOException {
    final AtomicReference<T> result = new AtomicReference<T>();
    final AtomicReference<IOException> exception = new AtomicReference<IOException>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          result.set(computable.compute());
        }
        catch (IOException e) {
          exception.set(e);
        }
      }
    });
    //noinspection ThrowableResultOfMethodCallIgnored
    if (exception.get() == null) {
      return result.get();
    }
    throw exception.get();
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

  private static boolean testConnection(final String url, final String login, final String password) throws IOException {
    GithubUser user = retrieveCurrentUserInfo(url, login, password);
    return user != null;
  }

  @Nullable
  private static GithubUser retrieveCurrentUserInfo(@NotNull String url, @NotNull String login,
                                                    @NotNull String password) throws IOException {
    JsonElement result = GithubApiUtil.getRequest(url, login, password, "/user");
    return parseUserInfo(result);
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
  private static List<RepositoryInfo> getAvailableRepos(@NotNull String url, @NotNull String login, @NotNull String password) {
    final String request = "/user/repos";
    try {
      JsonElement result = GithubApiUtil.getRequest(url, login, password, request);
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
      JsonElement jsonObject = GithubApiUtil.getRequest(url, login, password, request);
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

  public static boolean isPrivateRepoAllowed(final String url, final String login, final String password) throws IOException {
    GithubUser user = retrieveCurrentUserInfo(url, login, password);
    if (user == null) {
      return false;
    }
    return user.getPlan().isPrivateRepoAllowed();
  }

  /**
   * Checks if user has set up correct user credentials for GitHub access in the settings.
   * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
   */
  public static boolean checkCredentials(final Project project) {
    final GithubSettings settings = GithubSettings.getInstance();
    try {
      return checkCredentials(project, settings.getHost(), settings.getLogin(), settings.getPassword());
    }
    catch (IOException e) {
      // this method is a quick-check if we've got valid user setup.
      // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
      LOG.info(e);
      return false;
    }
  }

  public static boolean checkCredentials(Project project, final String url, final String login, final String password) throws IOException {
    if (StringUtil.isEmptyOrSpaces(url) || StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)){
      return false;
    }
    Boolean result = accessToGithubWithModalProgress(project, url, new ThrowableComputable<Boolean, IOException>() {
      @Override
      public Boolean compute() throws IOException {
        ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
        return testConnection(url, login, password);
      }
    });
    return result == null ? false : result;
  }

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   *
   * @param project
   * @return
   */
  @Nullable
  public static List<RepositoryInfo> getAvailableRepos(final Project project) throws IOException {
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
    return accessToGithubWithModalProgress(project, settings.getHost(), new ThrowableComputable<List<RepositoryInfo>, IOException>() {
      @Override
      public List<RepositoryInfo> compute() throws IOException {
        ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
        return getAvailableRepos(settings.getHost(), settings.getLogin(), validPassword);
      }
    });
  }

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static RepositoryInfo getDetailedRepositoryInfo(final Project project, final String owner, final String name) throws IOException {
    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final Boolean validCredentials = accessToGithubWithModalProgress(project, settings.getHost(),
                                                                     new ThrowableComputable<Boolean, IOException>() {
      @Override
      public Boolean compute() throws IOException {
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
    return accessToGithubWithModalProgress(project, settings.getHost(), new ThrowableComputable<RepositoryInfo, IOException>() {
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

    // TODO this doesn't work with organizational accounts
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

  static boolean isRepositoryOnGitHub(@NotNull GitRepository repository) {
    return findGithubRemoteUrl(repository) != null;
  }

  @Nullable
  static String findGithubRemoteUrl(@NotNull GitRepository repository) {
    for (GitRemote remote : repository.getRemotes()) {
      for (String url : remote.getUrls()) {
        if (isGithubUrl(url)) {
          return url;
        }
      }
    }
    return null;
  }

  public static boolean isGithubUrl(@NotNull String url) {
    return url.contains(GithubApiUtil.removeProtocolPrefix(GithubSettings.getInstance().getHost()));
  }

  static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  @Nullable
  public static String getUserAndRepositoryOrShowError(@NotNull Project project, @NotNull String url) {
    int index = -1;
    if (url.startsWith(getHttpsUrl())) {
      index = url.lastIndexOf('/');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository name: " + url, GithubOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER);
        return null;
      }
      index = url.substring(0, index).lastIndexOf('/');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository owner: " + url, GithubOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER);
        return null;
      }
    }
    else {
      index = url.lastIndexOf(':');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository name and owner: " + url, GithubOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER);
        return null;
      }
    }
    String repoInfo = url.substring(index + 1);
    if (repoInfo.endsWith(".git")) {
      repoInfo = repoInfo.substring(0, repoInfo.length() - 4);
    }
    return repoInfo;
  }

  @NotNull
  static String makeGithubRepoUrlFromRemoteUrl(@NotNull String remoteUrl) {
    remoteUrl = removeEndingDotGit(remoteUrl);
    if (remoteUrl.startsWith("http")) {
      return remoteUrl;
    }
    if (remoteUrl.startsWith("git://")) {
      return "https" + remoteUrl.substring(3);
    }
    return convertFromSshToHttp(remoteUrl);
  }

  @NotNull
  private static String convertFromSshToHttp(@NotNull String remoteUrl) {
    // Format: git@github.com:account/repository
    int indexOfAt = remoteUrl.indexOf("@");
    if (indexOfAt < 0) {
      throw new IllegalStateException("Invalid remote Github SSH url: " + remoteUrl);
    }
    String withoutPrefix = remoteUrl.substring(indexOfAt + 1, remoteUrl.length());
    return "https://" + withoutPrefix.replace(':', '/');
  }

  @NotNull
  private static String removeEndingDotGit(@NotNull String url) {
    final String DOT_GIT = ".git";
    if (url.endsWith(DOT_GIT)) {
      return url.substring(0, url.length() - DOT_GIT.length());
    }
    return url;
  }

  @NotNull
  public static String getErrorTextFromException(@NotNull IOException e) {
    return e.getMessage();
  }

  public static void notifyError(@NotNull Project project, @NotNull String title, @NotNull String message) {
    new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR).notify(project);
  }

}
