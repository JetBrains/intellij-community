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
package git4idea.jgit;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.push.GitSimplePushResult;
import git4idea.remote.GitRememberedInputs;
import git4idea.repo.GitRepository;
import git4idea.update.GitFetchResult;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles remote operations over HTTP via JGit library.
 *
 * @author Kirill Likhodedov
 */
public final class GitHttpAdapter {

  private static final Logger LOG = Logger.getInstance(GitHttpAdapter.class);

  /**
   * Nothing more than Runnable with exceptions.
   */
  private interface MyRunnable {
    void run() throws IOException, InvalidRemoteException;
  }
  
  private enum GeneralResult {
    SUCCESS,
    CANCELLED,
    NOT_AUTHORIZED
  }

  private GitHttpAdapter() {
  }

  /**
   * Fetches the given remote in the given Git repository.
   * Asks username and password if needed.
   */
  @NotNull
  public static GitFetchResult fetch(@NotNull final GitRepository repository, @NotNull final String remoteName, @NotNull final String remoteUrl)  {
    GitFetchResult.Type resultType;
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remoteUrl);
      GeneralResult result = callWithAuthRetry(new MyRunnable() {
        @Override
        public void run() throws InvalidRemoteException {
          FetchCommand fetchCommand = git.fetch();
          fetchCommand.setRemote(remoteName);
          fetchCommand.setCredentialsProvider(provider);
          fetchCommand.call();
        }
      }, provider);
      resultType = convertToFetchResultType(result);
    } catch (IOException e) {
      LOG.info("Exception while fetching " + remoteName + "(" + remoteUrl + ")" + " in " + repository.toLogString(), e);
      return GitFetchResult.error(e);
    }
    catch (InvalidRemoteException e) {
      LOG.info("Exception while fetching " + remoteName + "(" + remoteUrl + ")" + " in " + repository.toLogString(), e);
      return GitFetchResult.error(e);
    }
    return new GitFetchResult(resultType);
  }

  private static GitFetchResult.Type convertToFetchResultType(GeneralResult result) {
    switch (result) {
      case CANCELLED:      return GitFetchResult.Type.CANCELLED;
      case SUCCESS:        return GitFetchResult.Type.SUCCESS;
      case NOT_AUTHORIZED: return GitFetchResult.Type.NOT_AUTHORIZED;
    }
    return GitFetchResult.Type.CANCELLED;
  }

  @NotNull
  public static GitSimplePushResult push(@NotNull final GitRepository repository, @Nullable final String remoteName, @NotNull final String remoteUrl) {
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remoteUrl);
      final AtomicReference<GitSimplePushResult> pushResult = new AtomicReference<GitSimplePushResult>();
      GeneralResult result = callWithAuthRetry(new MyRunnable() {
        @Override
        public void run() throws InvalidRemoteException {
          PushCommand pushCommand = git.push();
          if (remoteName != null) {
            pushCommand.setRemote(remoteName);
          }
          pushCommand.setCredentialsProvider(provider);
          Iterable<PushResult> results = pushCommand.call();
          pushResult.set(analyzeResults(results));
        }
      }, provider);
      if (pushResult.get() == null) {
        return convertToPushResultType(result);
      } else {
        return pushResult.get();
      }
    }
    catch (InvalidRemoteException e) {
      LOG.info("Exception while pushing " + remoteName + "(" + remoteUrl + ")" + " in " + repository.toLogString(), e);
      return makeErrorResultFromException(e);
    }
    catch (IOException e) {
      LOG.info("Exception while pushing " + remoteName + "(" + remoteUrl + ")" + " in " + repository.toLogString(), e);
      return makeErrorResultFromException(e);
    }
  }

  @NotNull
  private static GitSimplePushResult convertToPushResultType(GeneralResult result) {
    switch (result) {
      case SUCCESS: 
        return GitSimplePushResult.success();
      case CANCELLED:
        return GitSimplePushResult.cancel();
      case NOT_AUTHORIZED:
        return GitSimplePushResult.notAuthorized();
      default:
        return GitSimplePushResult.cancel();
    }
  }

  @NotNull
  private static GitSimplePushResult analyzeResults(@NotNull Iterable<PushResult> results) {
    Collection<String> rejectedBranches = new ArrayList<String>();
    StringBuilder errorReport = new StringBuilder();
    
    for (PushResult result : results) {
      for (RemoteRefUpdate update : result.getRemoteUpdates()) {
        switch (update.getStatus()) {
          case REJECTED_NONFASTFORWARD:
            rejectedBranches.add(update.getSrcRef());
            // no break: add reject to the output
          case NON_EXISTING:
          case REJECTED_NODELETE:
          case REJECTED_OTHER_REASON:
          case REJECTED_REMOTE_CHANGED:
            errorReport.append(update.getSrcRef() + ": " + update.getStatus() + "<br/>");
          default:
            // on success do nothing
        }
      }
    }

    if (!rejectedBranches.isEmpty()) {
      return GitSimplePushResult.reject(rejectedBranches);
    } else if (errorReport.toString().isEmpty()) {
      return GitSimplePushResult.success();
    } else {
      return GitSimplePushResult.error(errorReport.toString());
    }
  }

  @NotNull
  private static GitSimplePushResult makeErrorResultFromException(Exception e) {
    return GitSimplePushResult.error(e.toString());
  }

  /**
   * Calls the given runnable.
   * If user cancels the authentication dialog, returns.
   * If user enters incorrect data, he has 2 more attempts to go before failure.
   */
  private static GeneralResult callWithAuthRetry(@NotNull MyRunnable command, GitHttpCredentialsProvider provider) throws InvalidRemoteException, IOException {
    for (int i = 0; i < 3; i++) {
      try {
        AuthData authData = getUsernameAndPassword(provider.getProject(), provider.getUrl());
        if (authData != null) {
          provider.fillAuthDataIfNotFilled(authData.getLogin(), authData.getPassword());
        }
        if (i == 0) {
          provider.setAlwaysShowDialog(false);   // if username and password are supplied, no need to show the dialog
        } else {
          provider.setAlwaysShowDialog(true);    // unless these values fail authentication
        }
        command.run();
        rememberPassword(provider);
        return GeneralResult.SUCCESS;
      } catch (JGitInternalException e) {
        if (!authError(e)) {
          throw e;
        } else if (provider.wasCancelled()) {  // if user cancels the dialog, just return
          return GeneralResult.CANCELLED;
        }
      }
    }
    return GeneralResult.NOT_AUTHORIZED;
  }

  private static void rememberPassword(@NotNull GitHttpCredentialsProvider credentialsProvider) {
    if (!credentialsProvider.wasDialogShown()) { // the dialog is not shown => everything is already stored
      return;
    }
    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    if (passwordSafe.getSettings().getProviderType() == PasswordSafeSettings.ProviderType.DO_NOT_STORE) {
      return;
    }
    String login = credentialsProvider.getUserName();
    if (login == null || credentialsProvider.getPassword() == null) {
      return;
    }

    String url = adjustHttpUrl(credentialsProvider.getUrl());
    String key = keyForUrlAndLogin(url, login);
    try {
      // store in memory always
      storePassword(passwordSafe.getMemoryProvider(), credentialsProvider, key);
      if (credentialsProvider.isRememberPassword()) {
        storePassword(passwordSafe.getMasterKeyProvider(), credentialsProvider, key);
      }
      GitRememberedInputs.getInstance().addUrl(url, login);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't store the password for key [" + key + "]", e);
    }
  }

  private static void storePassword(PasswordSafeProvider passwordProvider, GitHttpCredentialsProvider credentialsProvider, String key) throws PasswordSafeException {
    passwordProvider.storePassword(credentialsProvider.getProject(), GitHttpCredentialsProvider.class, key, credentialsProvider.getPassword());
  }

  @Nullable
  private static AuthData getUsernameAndPassword(Project project, String url) {
    url = adjustHttpUrl(url);
    String userName = GitRememberedInputs.getInstance().getUserNameForUrl(url);
    if (userName == null) {
      return null;
    }
    String key = keyForUrlAndLogin(url, userName);
    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    try {
      String password = passwordSafe.getMemoryProvider().getPassword(project, GitHttpCredentialsProvider.class, key);
      if (password == null) {
        password = passwordSafe.getMasterKeyProvider().getPassword(project, GitHttpCredentialsProvider.class, key);
      }
      return password != null ? new AuthData(userName, password) : null;
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't store the password for key [" + key + "]", e);
      return null;
    }
  }
  
  private static class AuthData {
    private final String myLogin;
    private final String myPassword;

    private AuthData(@NotNull String login, @NotNull String password) {
      myPassword = password;
      myLogin = login;
    }

    @NotNull
    public String getLogin() {
      return myLogin;
    }

    @NotNull
    public String getPassword() {
      return myPassword;
    }
  }
  

  /**
   * If url is HTTPS, store it as HTTP in the password database, not to make user enter and remember same credentials twice. 
   */
  @NotNull
  private static String adjustHttpUrl(@NotNull String url) {
    if (url.startsWith("https")) {
      return url.replaceFirst("https", "http");
    }
    return url;
  }

  @NotNull
  private static String keyForUrlAndLogin(@NotNull String stringUrl, @NotNull String login) {
    return login + ":" + stringUrl;
  }

  private static boolean authError(@NotNull JGitInternalException e) {
    Throwable cause = e.getCause();
    return (cause instanceof TransportException && cause.getMessage().contains("not authorized"));
  }

  public static boolean isHttpUrl(@NotNull String url) {
    return url.startsWith("http");
  }

  /**
   * Converts {@link GitRepository} to JGit's {@link Repository}.
   */
  @NotNull
  private static Repository convert(@NotNull GitRepository repository) throws IOException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    return builder.setGitDir(new File(repository.getRoot().getPath(), ".git"))
    .readEnvironment() // scan environment GIT_* variables
    .findGitDir()     // scan up the file system tree
    .build();
  }

  /**
   * Converts {@link GitRepository} to JGit's {@link Git} object.
   */
  private static Git convertToGit(@NotNull GitRepository repository) throws IOException {
    return Git.wrap(convert(repository));
  }

}
