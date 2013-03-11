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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.AuthData;
import com.intellij.util.proxy.CommonProxy;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.push.GitSimplePushResult;
import git4idea.remote.GitRememberedInputs;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Handles remote operations over HTTP via JGit library.
 *
 * @author Kirill Likhodedov
 */
public final class GitHttpAdapter {

  private static final Logger LOG = Logger.getInstance(GitHttpAdapter.class);

  private static final String IGNORECASE_SETTING = "ignorecase";

  public static boolean shouldUseJGit(@NotNull String url) {
    return "jgit".equals(System.getProperty("git.http"));
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
  public static GitFetchResult fetch(@NotNull final GitRepository repository, @NotNull final GitRemote remote,
                                     @NotNull String remoteUrl, @Nullable String remoteBranch)  {
    GitFetchResult.Type resultType;
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remoteUrl);

      List<String> specs;
      if (remoteBranch == null) {
        specs = remote.getFetchRefSpecs();
      }
      else {
        specs = Collections.singletonList(GitFetcher.getFetchSpecForBranch(remoteBranch, remote.getName()));
      }

      GeneralResult result = callWithAuthRetry(new GitHttpRemoteCommand.Fetch(git, provider, remoteUrl, convertRefSpecs(specs)),
                                               repository.getProject());
      resultType = convertToFetchResultType(result);
    } catch (IOException e) {
      logException(repository, remote.getName(), remoteUrl, e, "fetching");
      return GitFetchResult.error(e);
    }
    catch (InvalidRemoteException e) {
      logException(repository, remote.getName(), remoteUrl, e, "fetching");
      return GitFetchResult.error(e);
    }
    catch (URISyntaxException e) {
      logException(repository, remote.getName(), remoteUrl, e, "fetching");
      return GitFetchResult.error(e);
    }
    return new GitFetchResult(resultType);
  }

  @NotNull
  private static List<RefSpec> convertRefSpecs(@NotNull List<String> refSpecs) {
    List<RefSpec> jgitSpecs = new ArrayList<RefSpec>();
    for (String spec : refSpecs) {
      jgitSpecs.add(new RefSpec(spec));
    }
    return jgitSpecs;
  }

  private static void logException(GitRepository repository, String remoteName, String remoteUrl, Exception e, String operation) {
    LOG.error("Exception while " + operation + " " + remoteName + "(" + remoteUrl + ")" + " in " + repository.toLogString(), e);
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
  public static GitSimplePushResult push(@NotNull final GitRepository repository, @NotNull final String remoteName,
                                         @NotNull final String remoteUrl, @NotNull String pushSpec) {
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remoteUrl);
      GitHttpRemoteCommand.Push pushCommand = new GitHttpRemoteCommand.Push(git, provider, remoteName, remoteUrl,
                                                                            convertRefSpecs(Collections.singletonList(pushSpec)));
      GeneralResult result = callWithAuthRetry(pushCommand, repository.getProject());
      GitSimplePushResult pushResult = pushCommand.getResult();
      if (pushResult == null) {
        return convertToPushResultType(result);
      } else {
        return pushResult;
      }
    }
    catch (SmartPushNotSupportedException e) {
      return GitSimplePushResult.error("Remote <code>" + remoteUrl + "</code> doesn't support <a href=\"http://progit.org/2010/03/04/smart-http.html\">" +
                                       "smart HTTP push. </a><br/>" +
                                       "Please set the server to use smart push or use other protocol (SSH for example). <br/>" +
                                       "If neither is possible, as a workaround you may add authentication data directly to the remote url in <code>.git/config</code>.");
    }
    catch (InvalidRemoteException e) {
      logException(repository, remoteName, remoteUrl, e, "pushing");
      return makeErrorResultFromException(e);
    }
    catch (IOException e) {
      logException(repository, remoteName, remoteUrl, e, "pushing");
      return makeErrorResultFromException(e);
    }
    catch (URISyntaxException e) {
      logException(repository, remoteName, remoteUrl, e, "pushing");
      return makeErrorResultFromException(e);
    }
  }
  
  @NotNull
  public static Collection<String> lsRemote(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String remoteUrl) {
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remoteUrl);
      GitHttpRemoteCommand.LsRemote lsRemoteCommand = new GitHttpRemoteCommand.LsRemote(git, provider, remoteUrl);
      callWithAuthRetry(lsRemoteCommand, repository.getProject());
      return convertRefsToStrings(lsRemoteCommand.getRefs());
    } catch (IOException e) {
      logException(repository, remoteName, remoteUrl, e, "ls-remote");
    }
    catch (InvalidRemoteException e) {
      logException(repository, remoteName, remoteUrl, e, "ls-remote");
    }
    catch (URISyntaxException e) {
      logException(repository, remoteName, remoteUrl, e, "ls-remote");
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<String> convertRefsToStrings(@NotNull Collection<Ref> lsRemoteCommandRefs) {
    Collection<String> refs = new ArrayList<String>();
    for (Ref ref : lsRemoteCommandRefs) {
      String refName = ref.getName();
      if (refName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
        refName = refName.substring(GitBranch.REFS_HEADS_PREFIX.length());
      }
      refs.add(refName);
    }
    return refs;
  }

  @NotNull
  public static GitFetchResult cloneRepository(@NotNull Project project, @NotNull final File directory, @NotNull final String url) {
    GitFetchResult.Type resultType;
    try {
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(project, url);
      GitHttpRemoteCommand.Clone command = new GitHttpRemoteCommand.Clone(directory, provider, url);
      GeneralResult result = callWithAuthRetry(command, project);
      resultType = convertToFetchResultType(result);
      if (resultType.equals(GitFetchResult.Type.SUCCESS)) {
        updateCoreIgnoreCaseSetting(command.getGit());
      }
      return new GitFetchResult(resultType);
    }
    catch (InvalidRemoteException e) {
      LOG.info("Exception while cloning " + url + " to " + directory, e);
      return GitFetchResult.error(e);
    }
    catch (IOException e) {
      LOG.info("Exception while cloning " + url + " to " + directory, e);
      return GitFetchResult.error(e);
    }
    catch (URISyntaxException e) {
      LOG.info("Exception while cloning " + url + " to " + directory, e);
      return GitFetchResult.error(e);
    }
  }

  private static void updateCoreIgnoreCaseSetting(@Nullable Git git) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return;
    }
    if (git == null) {
      LOG.info("jgit.Git is null, the command should have failed. Not updating the settings.");
      return;
    }
    StoredConfig config = git.getRepository().getConfig();
    config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, IGNORECASE_SETTING, Boolean.TRUE.toString());
    try {
      config.save();
    }
    catch (IOException e) {
      LOG.info("Couldn't save config for " + git.getRepository().getDirectory().getPath(), e);
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
  private static GitSimplePushResult makeErrorResultFromException(Exception e) {
    return GitSimplePushResult.error(e.toString());
  }

  /**
   * Calls the given runnable.
   * If user cancels the authentication dialog, returns.
   * If user enters incorrect data, he has 2 more attempts to go before failure.
   * Cleanups are executed after each incorrect attempt to enter password, and after other retriable actions.
   */
  private static GeneralResult callWithAuthRetry(@NotNull GitHttpRemoteCommand command, @NotNull Project project) throws InvalidRemoteException, IOException, URISyntaxException {
    boolean httpTransportErrorFixTried = false;
    boolean noRemoteWithoutGitErrorFixTried = false;

    String url = command.getUrl();
    GitHttpCredentialsProvider provider = command.getCredentialsProvider();
    try {
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
        }
        catch (GitAPIException e) {
          if (!noRemoteWithoutGitErrorFixTried && isNoRemoteWithoutDotGitError(e, url)) {
            url = addDotGitToUrl(url);
            command.setUrl(url);
            provider.setUrl(url);
            noRemoteWithoutGitErrorFixTried = true;
            // don't "eat" one password entering attempt
            //noinspection AssignmentToForLoopParameter
            i--;
          }
          command.cleanup();
        }
        catch (JGitInternalException e) {
          try {
            if (authError(e)) {
              if (provider.wasCancelled()) {  // if user cancels the dialog, just return
                return GeneralResult.CANCELLED;
              }
              // otherwise give more tries to enter password
            }
            else if (!httpTransportErrorFixTried && isTransportExceptionForHttp(e, url)) {
              url = url.replaceFirst("http", "https");
              command.setUrl(url);
              provider.setUrl(url);
              httpTransportErrorFixTried = true;
              // don't "eat" one password entering attempt
              //noinspection AssignmentToForLoopParameter
              i--;
            }
            else if (!noRemoteWithoutGitErrorFixTried && isNoRemoteWithoutDotGitError(e, url)) {
              url = addDotGitToUrl(url);
              command.setUrl(url);
              provider.setUrl(url);
              noRemoteWithoutGitErrorFixTried = true;
              // don't "eat" one password entering attempt
              //noinspection AssignmentToForLoopParameter
              i--;
            }
            else if (smartHttpPushNotSupported(e)) {
              throw new SmartPushNotSupportedException(e.getCause().getMessage());
            }
            else {
              throw e;
            }
          }
          finally {
            command.cleanup();
          }
        }
      }
      return GeneralResult.NOT_AUTHORIZED;
    }
    finally {
      log(command, project);
    }
  }

  private static CommonProxy.HostInfo getHostInfo(String url) throws URISyntaxException {
    final boolean isSecure = url.startsWith("https");
    final String protocol = isSecure ? "https" : "http";
    final URI uri = new URI(url);
    int port = uri.getPort();
    port = port < 0 ? (isSecure ? 443 : 80) : port;
    return new CommonProxy.HostInfo(protocol, uri.getHost(), port);
  }

  @NotNull
  private static String addDotGitToUrl(@NotNull String url) {
    if (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    return url + GitUtil.DOT_GIT;
  }

  private static void log(@NotNull GitHttpRemoteCommand command, @NotNull Project project) {
    GitVcs vcs = GitVcs.getInstance(project);
    if (vcs != null) {
      vcs.showCommandLine(command.getCommandString());
    }
    LOG.info(command.getLogString());
  }

  private static boolean smartHttpPushNotSupported(JGitInternalException e) {
    if (e.getCause() instanceof NotSupportedException) {
      NotSupportedException nse = (NotSupportedException)e.getCause();
      String message = nse.getMessage();
      return message != null && message.toLowerCase().contains("smart http push");
    }
    return false;
  }

  private static boolean isNoRemoteWithoutDotGitError(Throwable e, String url) {
    Throwable cause = e.getCause();
    if (cause == null || (!(cause instanceof NoRemoteRepositoryException) && !(cause.getCause() instanceof NoRemoteRepositoryException))) {
      return false;
    }
    return !url.toLowerCase().endsWith(GitUtil.DOT_GIT);
  }

  private static boolean isTransportExceptionForHttp(@NotNull JGitInternalException e, @NotNull String url) {
     if (!(e.getCause() instanceof TransportException)) {
       return false;
     }
     return url.toLowerCase().startsWith("http") && !url.toLowerCase().startsWith("https");
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
      return trySavedAuthDataFromProviders(url);
    }
    String key = keyForUrlAndLogin(url, userName);
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    try {
      String password = passwordSafe.getPassword(project, GitHttpCredentialsProvider.class, key);
      if (password != null) {
        return new AuthData(userName, password);
      }
      return null;
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get the password for key [" + key + "]", e);
      return null;
    }
  }

  @Nullable
  private static AuthData trySavedAuthDataFromProviders(@NotNull String url) {
    GitHttpAuthDataProvider[] extensions = GitHttpAuthDataProvider.EP_NAME.getExtensions();
    for (GitHttpAuthDataProvider provider : extensions) {
      AuthData authData = provider.getAuthData(url);
      if (authData != null) {
        return authData;
      }
    }
    return null;
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

  /**
   * Converts {@link GitRepository} to JGit's {@link Repository}.
   */
  @NotNull
  private static Repository convert(@NotNull GitRepository repository) throws IOException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    return builder.setGitDir(new File(repository.getRoot().getPath(), GitUtil.DOT_GIT))
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

  private static class SmartPushNotSupportedException extends NotSupportedException {
    private SmartPushNotSupportedException(String message) {
      super(message);
    }
  }
}
