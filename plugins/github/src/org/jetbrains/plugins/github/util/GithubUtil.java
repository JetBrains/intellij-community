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
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.Convertor;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.GithubUserDetailed;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.exceptions.GithubTwoFactorAuthenticationException;
import org.jetbrains.plugins.github.ui.GithubBasicLoginDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.IOException;
import java.net.UnknownHostException;
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

  // TODO: Consider sharing of GithubAuthData between actions (as member of GithubSettings)
  public static <T> T runTask(@NotNull Project project,
                              @NotNull GithubAuthDataHolder authHolder,
                              @NotNull ProgressIndicator indicator,
                              @NotNull ThrowableConvertor<GithubAuthData, T, IOException> task) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      return task.convert(auth);
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      return runTask(project, authHolder, indicator, task);
    }
    catch (GithubAuthenticationException e) {
      getValidAuthData(project, authHolder, indicator, auth);
      return runTask(project, authHolder, indicator, task);
    }
  }

  public static void runTask(@NotNull Project project,
                             @NotNull GithubAuthDataHolder authHolder,
                             @NotNull ProgressIndicator indicator,
                             @NotNull ThrowableConsumer<GithubAuthData, IOException> task) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      task.consume(auth);
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      runTask(project, authHolder, indicator, task);
    }
    catch (GithubAuthenticationException e) {
      getValidAuthData(project, authHolder, indicator, auth);
      runTask(project, authHolder, indicator, task);
    }
  }

  public static <T> T runTaskWithBasicAuthForHost(@NotNull Project project,
                                                  @NotNull GithubAuthDataHolder authHolder,
                                                  @NotNull ProgressIndicator indicator,
                                                  @NotNull String host,
                                                  @NotNull ThrowableConvertor<GithubAuthData, T, IOException> task) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      if (auth.getAuthType() != GithubAuthData.AuthType.BASIC) {
        throw new GithubAuthenticationException("Expected basic authentication");
      }
      return task.convert(auth);
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      return runTaskWithBasicAuthForHost(project, authHolder, indicator, host, task);
    }
    catch (GithubAuthenticationException e) {
      getValidBasicAuthDataForHost(project, authHolder, indicator, auth, host);
      return runTaskWithBasicAuthForHost(project, authHolder, indicator, host, task);
    }
  }

  @NotNull
  private static GithubUserDetailed testConnection(@NotNull Project project,
                                                   @NotNull GithubAuthDataHolder authHolder,
                                                   @NotNull ProgressIndicator indicator) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      return GithubApiUtil.getCurrentUserDetailed(auth);
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      return testConnection(project, authHolder, indicator);
    }
  }

  public static void getValidAuthData(@NotNull final Project project,
                                      @NotNull final GithubAuthDataHolder authHolder,
                                      @NotNull final ProgressIndicator indicator,
                                      @NotNull final GithubAuthData oldAuth) throws GithubOperationCanceledException {
    authHolder.runTransaction(oldAuth, new ThrowableComputable<GithubAuthData, GithubOperationCanceledException>() {
      @Override
      @NotNull
      public GithubAuthData compute() throws GithubOperationCanceledException {
        final GithubLoginDialog dialog = new GithubLoginDialog(project, oldAuth);
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            DialogManager.show(dialog);
          }
        }, indicator.getModalityState());
        if (!dialog.isOK()) {
          throw new GithubOperationCanceledException("Can't get valid credentials");
        }
        GithubAuthData authData = dialog.getAuthData();

        GithubSettings.getInstance().setAuthData(authData, dialog.isSavePasswordSelected());
        return authData;
      }
    });
  }

  public static void getValidBasicAuthDataForHost(@NotNull final Project project,
                                                  @NotNull final GithubAuthDataHolder authHolder,
                                                  @NotNull final ProgressIndicator indicator,
                                                  @NotNull final GithubAuthData oldAuth,
                                                  @NotNull final String host) throws GithubOperationCanceledException {
    authHolder.runTransaction(oldAuth, new ThrowableComputable<GithubAuthData, GithubOperationCanceledException>() {
      @Override
      @NotNull
      public GithubAuthData compute() throws GithubOperationCanceledException {
        final GithubLoginDialog dialog = new GithubBasicLoginDialog(project, oldAuth, host);
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            DialogManager.show(dialog);
          }
        }, indicator.getModalityState());
        if (!dialog.isOK()) {
          throw new GithubOperationCanceledException("Can't get valid credentials");
        }
        GithubAuthData authData = dialog.getAuthData();

        final GithubSettings settings = GithubSettings.getInstance();
        if (settings.getAuthType() != GithubAuthData.AuthType.TOKEN) {
          GithubSettings.getInstance().setAuthData(authData, dialog.isSavePasswordSelected());
        }
        return authData;
      }
    });
  }

  private static void getTwoFactorAuthData(@NotNull final Project project,
                                           @NotNull final GithubAuthDataHolder authHolder,
                                           @NotNull final ProgressIndicator indicator,
                                           @NotNull final GithubAuthData oldAuth) throws GithubOperationCanceledException {
    authHolder.runTransaction(oldAuth, new ThrowableComputable<GithubAuthData, GithubOperationCanceledException>() {
      @Override
      @NotNull
      public GithubAuthData compute() throws GithubOperationCanceledException {
        if (authHolder.getAuthData().getAuthType() != GithubAuthData.AuthType.BASIC) {
          throw new GithubOperationCanceledException("Two factor authentication can be used only with Login/Password");
        }

        GithubApiUtil.askForTwoFactorCodeSMS(oldAuth);

        final Ref<String> codeRef = new Ref<String>();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            codeRef.set(Messages.showInputDialog(project, "Authentication Code", "Github Two-Factor Authentication", null));
          }
        }, indicator.getModalityState());
        if (codeRef.isNull()) {
          throw new GithubOperationCanceledException("Can't get two factor authentication code");
        }

        GithubSettings settings = GithubSettings.getInstance();
        if (settings.getAuthType() == GithubAuthData.AuthType.BASIC &&
            StringUtil.equalsIgnoreCase(settings.getLogin(), oldAuth.getBasicAuth().getLogin())) {
          settings.setValidGitAuth(false);
        }

        return oldAuth.copyWithTwoFactorCode(codeRef.get());
      }
    });
  }

  @NotNull
  public static GithubAuthDataHolder getValidAuthDataHolderFromConfig(@NotNull Project project, @NotNull ProgressIndicator indicator)
    throws IOException {
    GithubAuthData auth = GithubAuthData.createFromSettings();
    GithubAuthDataHolder authHolder = new GithubAuthDataHolder(auth);
    try {
      checkAuthData(project, authHolder, indicator);
      return authHolder;
    }
    catch (GithubAuthenticationException e) {
      getValidAuthData(project, authHolder, indicator, auth);
      return authHolder;
    }
  }

  @NotNull
  public static GithubUserDetailed checkAuthData(@NotNull Project project,
                                                 @NotNull GithubAuthDataHolder authHolder,
                                                 @NotNull ProgressIndicator indicator) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();

    if (StringUtil.isEmptyOrSpaces(auth.getHost())) {
      throw new GithubAuthenticationException("Target host not defined");
    }

    switch (auth.getAuthType()) {
      case BASIC:
        GithubAuthData.BasicAuth basicAuth = auth.getBasicAuth();
        assert basicAuth != null;
        if (StringUtil.isEmptyOrSpaces(basicAuth.getLogin()) || StringUtil.isEmptyOrSpaces(basicAuth.getPassword())) {
          throw new GithubAuthenticationException("Empty login or password");
        }
        break;
      case TOKEN:
        GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
        assert tokenAuth != null;
        if (StringUtil.isEmptyOrSpaces(tokenAuth.getToken())) {
          throw new GithubAuthenticationException("Empty token");
        }
        break;
      case ANONYMOUS:
        throw new GithubAuthenticationException("Anonymous connection not allowed");
    }

    return testConnection(project, authHolder, indicator);
  }

  public static <T> T computeValueInModal(@NotNull Project project,
                                          @NotNull String caption,
                                          @NotNull final ThrowableConvertor<ProgressIndicator, T, IOException> task) throws IOException {
    final Ref<T> dataRef = new Ref<T>();
    final Ref<Throwable> exceptionRef = new Ref<Throwable>();
    ProgressManager.getInstance().run(new Task.Modal(project, caption, true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          dataRef.set(task.convert(indicator));
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
        catch (Error e) {
          exceptionRef.set(e);
        }
        catch (RuntimeException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      Throwable e = exceptionRef.get();
      if (e instanceof IOException) throw ((IOException)e);
      if (e instanceof RuntimeException) throw ((RuntimeException)e);
      if (e instanceof Error) throw ((Error)e);
      throw new RuntimeException(e);
    }
    return dataRef.get();
  }

  public static <T> T computeValueInModal(@NotNull Project project,
                                          @NotNull String caption,
                                          @NotNull final Convertor<ProgressIndicator, T> task) {
    final Ref<T> dataRef = new Ref<T>();
    final Ref<Throwable> exceptionRef = new Ref<Throwable>();
    ProgressManager.getInstance().run(new Task.Modal(project, caption, true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          dataRef.set(task.convert(indicator));
        }
        catch (Error e) {
          exceptionRef.set(e);
        }
        catch (RuntimeException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      Throwable e = exceptionRef.get();
      if (e instanceof RuntimeException) throw ((RuntimeException)e);
      if (e instanceof Error) throw ((Error)e);
      throw new RuntimeException(e);
    }
    return dataRef.get();
  }

  public static void computeValueInModal(@NotNull Project project,
                                         @NotNull String caption,
                                         @NotNull final Consumer<ProgressIndicator> task) {
    final Ref<Throwable> exceptionRef = new Ref<Throwable>();
    ProgressManager.getInstance().run(new Task.Modal(project, caption, true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          task.consume(indicator);
        }
        catch (Error e) {
          exceptionRef.set(e);
        }
        catch (RuntimeException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      Throwable e = exceptionRef.get();
      if (e instanceof RuntimeException) throw ((RuntimeException)e);
      if (e instanceof Error) throw ((Error)e);
      throw new RuntimeException(e);
    }
  }

  /*
  * Git utils
  */

  @Nullable
  public static String findGithubRemoteUrl(@NotNull GitRepository repository) {
    Pair<GitRemote, String> remote = findGithubRemote(repository);
    if (remote == null) {
      return null;
    }
    return remote.getSecond();
  }

  @Nullable
  public static Pair<GitRemote, String> findGithubRemote(@NotNull GitRepository repository) {
    Pair<GitRemote, String> githubRemote = null;
    for (GitRemote gitRemote : repository.getRemotes()) {
      for (String remoteUrl : gitRemote.getUrls()) {
        if (GithubUrlUtil.isGithubUrl(remoteUrl)) {
          final String remoteName = gitRemote.getName();
          if ("github".equals(remoteName) || "origin".equals(remoteName)) {
            return Pair.create(gitRemote, remoteUrl);
          }
          if (githubRemote == null) {
            githubRemote = Pair.create(gitRemote, remoteUrl);
          }
          break;
        }
      }
    }
    return githubRemote;
  }

  @Nullable
  public static String findUpstreamRemote(@NotNull GitRepository repository) {
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

  @Nullable
  public static GitRemote findGithubRemote(@NotNull GitRepository gitRepository, @NotNull GithubFullPath path) {
    for (GitRemote remote : gitRepository.getRemotes()) {
      for (String url : remote.getUrls()) {
        if (path.equals(GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url))) {
          return remote;
        }
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
      GithubNotifications.showErrorDialog(project, GitBundle.getString("find.git.error.title"), e);
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

  public static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  @NotNull
  public static String getErrorTextFromException(@NotNull Exception e) {
    if (e instanceof UnknownHostException) {
      return "Unknown host: " + e.getMessage();
    }
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

  public static boolean addGithubRemote(@NotNull Project project,
                                        @NotNull GitRepository repository,
                                        @NotNull String remote,
                                        @NotNull String url) {
    final GitSimpleHandler handler = new GitSimpleHandler(project, repository.getRoot(), GitCommand.REMOTE);
    handler.setSilent(true);

    try {
      handler.addParameters("add", remote, url);
      handler.run();
      if (handler.getExitCode() != 0) {
        GithubNotifications.showError(project, "Can't add remote", "Failed to add GitHub remote: '" + url + "'. " + handler.getStderr());
        return false;
      }
      // catch newly added remote
      repository.update();
      return true;
    }
    catch (VcsException e) {
      GithubNotifications.showError(project, "Can't add remote", e);
      return false;
    }
  }
}
