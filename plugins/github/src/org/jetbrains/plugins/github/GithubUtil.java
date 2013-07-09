/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import git4idea.GitUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Various utility methods for the GutHub plugin.
 *
 * @author oleg
 * @author Kirill Likhodedov
 * @author Aleksey Pivovarov
 */
public class GithubUtil {

  public static final Logger LOG = Logger.getInstance("github");

  @Nullable
  public static GithubAuthData runAndGetValidAuth(@NotNull Project project,
                                                  @NotNull ProgressIndicator indicator,
                                                  @NotNull ThrowableConsumer<GithubAuthData, IOException> task) throws IOException {
    GithubAuthData auth = GithubSettings.getInstance().getAuthData();
    try {
      task.consume(auth);
      return auth;
    }
    catch (AuthenticationException e) {
      auth = getValidAuthData(project, indicator);
      if (auth == null) {
        return null;
      }
      task.consume(auth);
      return auth;
    }
    catch (IOException e) {
      GithubSslSupport sslSupport = GithubSslSupport.getInstance();
      if (GithubSslSupport.isCertificateException(e)) {
        if (sslSupport.askIfShouldProceed(auth.getHost())) {
          return runAndGetValidAuth(project, indicator, task);
        }
        else {
          return null;
        }
      }
      throw e;
    }
  }

  @Nullable
  public static <T> T runWithValidAuth(@NotNull Project project,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull ThrowableConvertor<GithubAuthData, T, IOException> task) throws IOException {
    GithubAuthData auth = GithubSettings.getInstance().getAuthData();
    try {
      return task.convert(auth);
    }
    catch (AuthenticationException e) {
      auth = getValidAuthData(project, indicator);
      if (auth == null) {
        return null;
      }
      return task.convert(auth);
    }
    catch (IOException e) {
      GithubSslSupport sslSupport = GithubSslSupport.getInstance();
      if (GithubSslSupport.isCertificateException(e)) {
        if (sslSupport.askIfShouldProceed(auth.getHost())) {
          return runWithValidAuth(project, indicator, task);
        }
        else {
          return null;
        }
      }
      throw e;
    }
  }

  @Nullable
  public static GithubAuthData getValidAuthData(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    final GithubLoginDialog dialog = new GithubLoginDialog(project);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        dialog.show();
      }
    }, indicator.getModalityState());
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.getAuthData();
  }

  @Nullable
  public static GithubAuthData getValidAuthDataFromConfig(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GithubAuthData auth = GithubSettings.getInstance().getAuthData();
    boolean valid = false;
    try {
      valid = checkAuthData(auth);
    }
    catch (IOException e) {
      LOG.error("Connection error", e);
    }
    if (!valid) {
      return getValidAuthData(project, indicator);
    }
    else {
      return auth;
    }
  }

  public static boolean checkAuthData(GithubAuthData auth) throws IOException {
    if (StringUtil.isEmptyOrSpaces(auth.getHost()) ||
        StringUtil.isEmptyOrSpaces(auth.getLogin()) ||
        StringUtil.isEmptyOrSpaces(auth.getPassword())) {
      return false;
    }

    try {
      return testConnection(auth);
    }
    catch (AuthenticationException e) {
      return false;
    }
  }

  private static boolean testConnection(@NotNull GithubAuthData auth) throws IOException {
    GithubUser user = getCurrentUserInfo(auth);
    return user != null;
  }

  @Nullable
  public static GithubUser getCurrentUserInfo(@NotNull GithubAuthData auth) throws IOException {
    JsonElement result = GithubApiUtil.getRequest(auth, "/user");
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
    String login = obj.get("login").getAsString();
    int privateRepos = obj.get("owned_private_repos").getAsInt();
    int maxPrivateRepos = obj.get("plan").getAsJsonObject().get("private_repos").getAsInt();
    return new GithubUser(login, privateRepos, maxPrivateRepos);
  }

  @NotNull
  public static List<RepositoryInfo> getAvailableRepos(@NotNull GithubAuthData auth) throws IOException {
    return doGetAvailableRepos(auth, null);
  }

  @NotNull
  public static List<RepositoryInfo> getAvailableRepos(@NotNull GithubAuthData auth, @NotNull String user) throws IOException {
    return doGetAvailableRepos(auth, user);
  }

  @NotNull
  private static List<RepositoryInfo> doGetAvailableRepos(@NotNull GithubAuthData auth, @Nullable String user) throws IOException {
    String request = user == null ? "/user/repos" : "/users/" + user + "/repos";
    JsonElement result = GithubApiUtil.getRequest(auth, request);
    if (result == null) {
      return Collections.emptyList();
    }
    return parseRepositoryInfos(result);
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
    String browserUrl = result.get("html_url").getAsString();
    String cloneUrl = result.get("clone_url").getAsString();
    String ownerName = result.get("owner").getAsJsonObject().get("login").getAsString();
    String parentName = result.has("parent") ? result.get("parent").getAsJsonObject().get("full_name").getAsString() : null;
    boolean fork = result.get("fork").getAsBoolean();
    return new RepositoryInfo(name, browserUrl, cloneUrl, ownerName, parentName, fork);
  }

  @Nullable
  public static RepositoryInfo getDetailedRepoInfo(@NotNull GithubAuthData auth, @NotNull String owner, @NotNull String name)
    throws IOException {
    final String request = "/repos/" + owner + "/" + name;
    JsonElement jsonObject = GithubApiUtil.getRequest(auth, request);
    if (jsonObject == null) {
      LOG.info(String.format("Information about repository is unavailable. Owner: %s, Name: %s", owner, name));
      return null;
    }
    return parseSingleRepositoryInfo(jsonObject.getAsJsonObject());
  }

  public static void deleteGithubRepository(@NotNull GithubAuthData auth, @NotNull String repo) throws IOException {
    String path = "/repos/" + auth.getLogin() + "/" + repo;
    GithubApiUtil.deleteRequest(auth, path);
  }

  public static void deleteGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    String path = "/gists/" + id;
    GithubApiUtil.deleteRequest(auth, path);
  }

  @Nullable
  public static JsonObject getGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
    String path = "/gists/" + id;
    JsonElement result = GithubApiUtil.getRequest(auth, path);
    if (result == null) {
      return null;
    }
    return result.getAsJsonObject();
  }

  @Nullable
  public static String findGithubRemoteUrl(@NotNull GitRepository repository) {
    String githubUrl = null;
    for (GitRemote gitRemote : repository.getRemotes()) {
      for (String remoteUrl : gitRemote.getUrls()) {
        if (GithubUrlUtil.isGithubUrl(remoteUrl)) {
          final String remoteName = gitRemote.getName();
          if ("github".equals(remoteName) || "origin".equals(remoteName)) {
            return remoteUrl;
          }
          if (githubUrl == null) {
            githubUrl = remoteUrl;
          }
          break;
        }
      }
    }
    return githubUrl;
  }

  @Nullable
  public static String findGithubUpstreamRemote(@NotNull GitRepository repository) {
    for (GitRemote gitRemote : repository.getRemotes()) {
      final String remoteName = gitRemote.getName();
      if ("upstream".equals(remoteName)) {
        for (String remoteUrl : gitRemote.getUrls()) {
          if (GithubUrlUtil.isGithubUrl(remoteUrl)) {
            return remoteUrl;
          }
        }
        return gitRemote.getFirstUrl();
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
    }
    catch (Exception e) {
      GithubNotifications.showErrorDialog(project, GitBundle.getString("find.git.error.title"), e.getMessage());
      return false;
    }

    if (!version.isSupported()) {
      GithubNotifications.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                                            GitBundle.getString("find.git.success.title"));
      return false;
    }
    return true;
  }

  public static boolean isRepositoryOnGitHub(@NotNull GitRepository repository) {
    return findGithubRemoteUrl(repository) != null;
  }

  static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  @NotNull
  public static String getErrorTextFromException(@NotNull IOException e) {
    return e.getMessage();
  }

  @Nullable
  public static GitRepository getGitRepository(@NotNull Project project, @Nullable VirtualFile file) {
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    List<GitRepository> repositories = manager.getRepositories();
    if (repositories.size() == 0) {
      return null;
    }
    if (repositories.size() == 1) {
      return repositories.get(0);
    }
    if (file != null) {
      GitRepository repository = manager.getRepositoryForFile(file);
      if (repository != null) {
        return repository;
      }
    }
    return manager.getRepositoryForFile(project.getBaseDir());
  }

}
