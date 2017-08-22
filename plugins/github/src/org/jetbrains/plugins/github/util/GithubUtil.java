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
package org.jetbrains.plugins.github.util;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
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
import org.jetbrains.plugins.github.api.GithubConnection;
import org.jetbrains.plugins.github.api.data.GithubUserDetailed;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.exceptions.GithubTwoFactorAuthenticationException;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
                              @NotNull final ProgressIndicator indicator,
                              @NotNull ThrowableConvertor<GithubConnection, T, IOException> task) throws IOException {
    return runTask(project, authHolder, indicator, AuthLevel.LOGGED, task);
  }

  public static <T> T runTask(@NotNull Project project,
                              @NotNull GithubAuthDataHolder authHolder,
                              @NotNull final ProgressIndicator indicator,
                              @NotNull AuthLevel authLevel,
                              @NotNull ThrowableConvertor<GithubConnection, T, IOException> task) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      if (!authLevel.accepts(auth)) {
        throw new GithubAuthenticationException("Expected other authentication type: " + authLevel);
      }

      final GithubConnection connection = new GithubConnection(auth, true);
      ScheduledFuture<?> future = null;

      try {
        future = addCancellationListener(indicator, connection);
        return task.convert(connection);
      }
      finally {
        connection.close();
        if (future != null) future.cancel(true);
      }
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      return runTask(project, authHolder, indicator, authLevel, task);
    }
    catch (GithubAuthenticationException e) {
      getValidAuthData(project, authHolder, indicator, authLevel, auth);
      return runTask(project, authHolder, indicator, authLevel, task);
    }
  }

  @NotNull
  private static GithubUserDetailed testConnection(@NotNull Project project,
                                                   @NotNull GithubAuthDataHolder authHolder,
                                                   @NotNull final ProgressIndicator indicator) throws IOException {
    GithubAuthData auth = authHolder.getAuthData();
    try {
      final GithubConnection connection = new GithubConnection(auth, true);
      ScheduledFuture<?> future = null;

      try {
        future = addCancellationListener(indicator, connection);
        return GithubApiUtil.getCurrentUser(connection);
      }
      finally {
        connection.close();
        if (future != null) future.cancel(true);
      }
    }
    catch (GithubTwoFactorAuthenticationException e) {
      getTwoFactorAuthData(project, authHolder, indicator, auth);
      return testConnection(project, authHolder, indicator);
    }
  }

  @NotNull
  private static ScheduledFuture<?> addCancellationListener(@NotNull Runnable run) {
    return JobScheduler.getScheduler().scheduleWithFixedDelay(run, 1000, 300, TimeUnit.MILLISECONDS);
  }

  @NotNull
  private static ScheduledFuture<?> addCancellationListener(@NotNull final ProgressIndicator indicator,
                                                            @NotNull final GithubConnection connection) {
    return addCancellationListener(() -> {
      if (indicator.isCanceled()) connection.abort();
    });
  }

  @NotNull
  private static ScheduledFuture<?> addCancellationListener(@NotNull final ProgressIndicator indicator,
                                                            @NotNull final Thread thread) {
    return addCancellationListener(() -> {
      if (indicator.isCanceled()) thread.interrupt();
    });
  }

  private static void getValidAuthData(@NotNull final Project project,
                                       @NotNull final GithubAuthDataHolder authHolder,
                                       @NotNull final ProgressIndicator indicator,
                                       @NotNull final AuthLevel authLevel,
                                       @NotNull final GithubAuthData oldAuth) throws GithubOperationCanceledException {
    authHolder.runTransaction(oldAuth, () -> {
      final GithubAuthData[] authData = new GithubAuthData[1];
      ApplicationManager.getApplication().invokeAndWait(() -> {
        GithubLoginDialog dialog = new GithubLoginDialog(project, oldAuth, authLevel);
        DialogManager.show(dialog);
        if (dialog.isOK()) {
          authData[0] = dialog.getAuthData();

          if (!authLevel.isOnetime()) {
            GithubSettings.getInstance().setAuthData(authData[0], dialog.isSavePasswordSelected());
          }
        }
      }, indicator.getModalityState());

      if (authData[0] == null) throw new GithubOperationCanceledException("Can't get valid credentials");
      return authData[0];
    });
  }

  private static void getTwoFactorAuthData(@NotNull final Project project,
                                           @NotNull final GithubAuthDataHolder authHolder,
                                           @NotNull final ProgressIndicator indicator,
                                           @NotNull final GithubAuthData oldAuth) throws GithubOperationCanceledException {
    authHolder.runTransaction(oldAuth, () -> {
      if (authHolder.getAuthData().getAuthType() != GithubAuthData.AuthType.BASIC) {
        throw new GithubOperationCanceledException("Two factor authentication can be used only with Login/Password");
      }

      GithubApiUtil.askForTwoFactorCodeSMS(new GithubConnection(oldAuth, false));

      final Ref<String> codeRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        codeRef.set(Messages.showInputDialog(project, "Authentication Code", "Github Two-Factor Authentication", null));
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
    });
  }

  @NotNull
  public static GithubAuthDataHolder getValidAuthDataHolderFromConfig(@NotNull Project project,
                                                                      @NotNull AuthLevel authLevel,
                                                                      @NotNull ProgressIndicator indicator)
    throws IOException {
    GithubAuthData auth = GithubAuthData.createFromSettings();
    GithubAuthDataHolder authHolder = new GithubAuthDataHolder(auth);
    try {
      if (!authLevel.accepts(auth)) throw new GithubAuthenticationException("Expected other authentication type: " + authLevel);
      checkAuthData(project, authHolder, indicator);
      return authHolder;
    }
    catch (GithubAuthenticationException e) {
      getValidAuthData(project, authHolder, indicator, authLevel, auth);
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

    try {
      new URI(auth.getHost());
    }
    catch (URISyntaxException e) {
      throw new GithubAuthenticationException("Invalid host URL");
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

  public static <T> T computeValueInModalIO(@NotNull Project project,
                                            @NotNull String caption,
                                            @NotNull final ThrowableConvertor<ProgressIndicator, T, IOException> task) throws IOException {
    return ProgressManager.getInstance().run(new Task.WithResult<T, IOException>(project, caption, true) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws IOException {
        return task.convert(indicator);
      }
    });
  }

  public static <T> T computeValueInModal(@NotNull Project project,
                                          @NotNull String caption,
                                          @NotNull final Convertor<ProgressIndicator, T> task) {
    return computeValueInModal(project, caption, true, task);
  }

  public static <T> T computeValueInModal(@NotNull Project project,
                                          @NotNull String caption,
                                          boolean canBeCancelled,
                                          @NotNull final Convertor<ProgressIndicator, T> task) {
    return ProgressManager.getInstance().run(new Task.WithResult<T, RuntimeException>(project, caption, canBeCancelled) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) {
        return task.convert(indicator);
      }
    });
  }

  public static void computeValueInModal(@NotNull Project project,
                                         @NotNull String caption,
                                         boolean canBeCancelled,
                                         @NotNull final Consumer<ProgressIndicator> task) {
    ProgressManager.getInstance().run(new Task.WithResult<Void, RuntimeException>(project, caption, canBeCancelled) {
      @Override
      protected Void compute(@NotNull ProgressIndicator indicator) {
        task.consume(indicator);
        return null;
      }
    });
  }

  public static <T> T runInterruptable(@NotNull final ProgressIndicator indicator,
                                       @NotNull ThrowableComputable<T, IOException> task) throws IOException {
    ScheduledFuture<?> future = null;
    try {
      final Thread thread = Thread.currentThread();
      future = addCancellationListener(indicator, thread);

      return task.compute();
    }
    finally {
      if (future != null) future.cancel(true);
      Thread.interrupted();
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
      GithubNotifications.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.getPresentation(), GitVersion.MIN.getPresentation()),
                                            GitBundle.getString("find.git.success.title"));
      return false;
    }
    return true;
  }

  public static boolean isRepositoryOnGitHub(@NotNull GitRepository repository) {
    return findGithubRemoteUrl(repository) != null;
  }

  @NotNull
  public static String getErrorTextFromException(@NotNull Exception e) {
    if (e instanceof UnknownHostException) {
      return "Unknown host: " + e.getMessage();
    }
    return StringUtil.notNullize(e.getMessage(), "Unknown error");
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
      GitRepository repository = manager.getRepositoryForFileQuick(file);
      if (repository != null) {
        return repository;
      }
    }
    return manager.getRepositoryForFileQuick(project.getBaseDir());
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

  /**
   * Splits full commit message into subject and description in GitHub style:
   * First line becomes subject, everything after first line becomes description
   * Also supports empty line that separates subject and description
   *
   * @param commitMessage full commit message
   * @return couple of subject and description based on full commit message
   */
  public static Couple<String> getGithubLikeFormattedDescriptionMessage(String commitMessage) {
    //Trim original
    String message = commitMessage == null ? "" : commitMessage.trim();
    if (message.isEmpty()) {
      return Couple.of("", "");
    }
    int firstLineEnd = message.indexOf("\n");
    String subject;
    String description;
    if (firstLineEnd > -1) {
      //Subject is always first line
      subject = message.substring(0, firstLineEnd).trim();
      //Description is all text after first line, we also trim it to remove empty lines on start of description
      description = message.substring(firstLineEnd + 1).trim();
    } else {
      //If we don't have any line separators and cannot detect description,
      //we just assume that it is one-line commit and use full message as subject with empty description
      subject = message;
      description = "";
    }

    return Couple.of(subject, description);
  }
}
