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
package git4idea.commands;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.ui.PasswordSafePromptDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.intellij.util.UriUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.vcsUtil.AuthDialog;
import git4idea.jgit.GitHttpAuthDataProvider;
import git4idea.remote.GitRememberedInputs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Handles "ask username" and "ask password" requests from Git:
 *    shows authentication dialog in the GUI, waits for user input and returns the credentials supplied by the user.</p>
 * <p>If user cancels the dialog, empty string is returned.</p>
 * <p>If no username is specified in the URL, Git queries for the username and for the password consecutively.
 *    In this case to avoid showing dialogs twice, the component asks for both credentials at once,
 *    and remembers the password to provide it to the Git process during the next request without requiring user interaction.</p>
 * <p>New instance of the GitAskPassGuiHandler should be created for each session, i. e. for each remote operation call.</p>
 *
 * @author Kirill Likhodedov
 */
class GitHttpGuiAuthenticator implements GitHttpAuthenticator {

  private static final Logger LOG = Logger.getInstance(GitHttpGuiAuthenticator.class);
  private static final Class<GitHttpAuthenticator> PASS_REQUESTER = GitHttpAuthenticator.class;

  @NotNull  private final Project myProject;
  @Nullable private final ModalityState myModalityState;
  @NotNull  private final String myTitle;
  @NotNull private final String myUrlFromCommand;

  @Nullable private String myPassword;
  @Nullable private String myPasswordKey;
  @Nullable private String myUrl;
  @Nullable private String myLogin;
  private boolean myRememberOnDisk;

  GitHttpGuiAuthenticator(@NotNull Project project, @Nullable ModalityState modalityState, @NotNull GitCommand command,
                          @NotNull String url) {
    myProject = project;
    myModalityState = modalityState;
    myTitle = "Git " + StringUtil.capitalize(command.name());
    myUrlFromCommand = url;
  }

  @Override
  @NotNull
  public String askPassword(@NotNull String url) {
    if (myPassword != null) {  // already asked in askUsername
      return myPassword;
    }
    url = adjustUrl(url);
    String prompt = "Enter the password for " + url;
    myPasswordKey = url;
    String password = PasswordSafePromptDialog.askPassword(myProject, myModalityState, myTitle, prompt, PASS_REQUESTER, url, false, null);
    if (password == null) {
      return "";
    }
    // Password is stored in the safe in PasswordSafePromptDialog.askPassword,
    // but it is not the right behavior (incorrect password is stored too because of that) and should be fixed separately.
    // We store it here manually, to let it work after that behavior is fixed.
    myPassword = password;
    return password;
  }

  @Override
  @NotNull
  public String askUsername(@NotNull String url) {
    url = adjustUrl(url);
    AuthData authData = getSavedAuthData(myProject, url);
    String login = null;
    String password = null;
    if (authData != null) {
      login = authData.getLogin();
      password = authData.getPassword();
    }
    if (login != null && password != null) {
      myPassword = password;
      return login;
    }

    final AuthDialog dialog = new AuthDialog(myProject, myTitle, "Enter credentials for " + url, login, null, true);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        dialog.show();
      }
    }, myModalityState == null ? ModalityState.defaultModalityState() : myModalityState);

    if (!dialog.isOK()) {
      return "";
    }

    // remember values to store in the database afterwards, if authentication succeeds
    myPassword = dialog.getPassword();
    myLogin = dialog.getUsername();
    myUrl = url;
    myRememberOnDisk = dialog.isRememberPassword();
    myPasswordKey = makeKey(myUrl, myLogin);

    return myLogin;
  }

  @Override
  public void saveAuthData() {
    // save login and url
    if (myUrl != null && myLogin != null) {
      GitRememberedInputs.getInstance().addUrl(myUrl, myLogin);
    }

    // save password
    if (myPasswordKey != null && myPassword != null) {
      PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
      try {
        passwordSafe.getMemoryProvider().storePassword(myProject, PASS_REQUESTER, myPasswordKey, myPassword);
        if (myRememberOnDisk) {
          passwordSafe.getMasterKeyProvider().storePassword(myProject, PASS_REQUESTER, myPasswordKey, myPassword);
        }
      }
      catch (PasswordSafeException e) {
        LOG.error("Couldn't remember password for " + myPasswordKey, e);
      }
    }
  }

  @Override
  public void forgetPassword() {
    if (myPasswordKey != null) {
      try {
        PasswordSafe.getInstance().removePassword(myProject, PASS_REQUESTER, myPasswordKey);
      }
      catch (PasswordSafeException e) {
        LOG.info("Couldn't forget the password for " + myPasswordKey);
      }
    }
  }

  @NotNull
  private String adjustUrl(@NotNull String url) {
    if (StringUtil.isEmptyOrSpaces(url)) {
      // if Git doesn't specify the URL in the username/password query, we use the url from the Git command
      // We only take the host, to avoid entering the same password for different repositories on the same host.
      return adjustHttpUrl(getHost(myUrlFromCommand));
    }
    return adjustHttpUrl(url);
  }

  @NotNull
  private static String getHost(@NotNull String url) {
    Pair<String, String> split = UriUtil.splitScheme(url);
    String scheme = split.getFirst();
    String urlItself = split.getSecond();
    int pathStart = urlItself.indexOf("/");
    return scheme + URLUtil.SCHEME_SEPARATOR + urlItself.substring(0, pathStart);
  }

  /**
   * If the url scheme is HTTPS, store it as HTTP in the database, not to make user enter and remember same credentials twice.
   */
  @NotNull
  private static String adjustHttpUrl(@NotNull String url) {
    String prefix = "https";
    if (url.startsWith(prefix)) {
      return "http" + url.substring(prefix.length());
    }
    return url;
  }

  @Nullable
  private static AuthData getSavedAuthData(@NotNull Project project, @NotNull String url) {
    String userName = GitRememberedInputs.getInstance().getUserNameForUrl(url);
    if (userName == null) {
      return trySavedAuthDataFromProviders(url);
    }
    String key = makeKey(url, userName);
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    try {
      String password = passwordSafe.getPassword(project, PASS_REQUESTER, key);
      if (password != null) {
        return new AuthData(userName, password);
      }
      return trySavedAuthDataFromProviders(url);
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
   * Makes the password database key for the URL: inserts the login after the scheme: http://login@url.
   */
  @NotNull
  private static String makeKey(@NotNull String url, @NotNull String login) {
    Pair<String,String> pair = UriUtil.splitScheme(url);
    String scheme = pair.getFirst();
    if (StringUtil.isEmpty(scheme)) {
      return scheme + URLUtil.SCHEME_SEPARATOR + login + "@" + pair.getSecond();
    }
    return login + "@" + url;
  }

}
