/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.intellij.util.ObjectUtils;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.vcsUtil.AuthDialog;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.GitRememberedInputs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

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

  @NotNull private final Project myProject;
  @NotNull private final String myTitle;
  @NotNull private final Collection<String> myUrlsFromCommand;

  @Nullable private String myPassword;
  @Nullable private String myPasswordKey;
  @Nullable private String myUnifiedUrl;
  @Nullable private String myLogin;
  private boolean mySaveOnDisk;
  @Nullable private GitHttpAuthDataProvider myDataProvider;
  private boolean myWasCancelled;

  GitHttpGuiAuthenticator(@NotNull Project project, @NotNull GitCommand command, @NotNull Collection<String> url) {
    myProject = project;
    myTitle = "Git " + StringUtil.capitalize(command.name());
    myUrlsFromCommand = url;
  }

  @Override
  @NotNull
  public String askPassword(@NotNull String url) {
    LOG.debug("askPassword. url=" + url + ", passwordKnown=" + (myPassword != null) + ", wasCancelled=" + myWasCancelled);
    if (myPassword != null) {  // already asked in askUsername
      return myPassword;
    }
    if (myWasCancelled) { // already pressed cancel in askUsername
      return "";
    }
    myUnifiedUrl = getUnifiedUrl(url);
    Pair<GitHttpAuthDataProvider, AuthData> authData = findBestAuthData(getUnifiedUrl(url));
    if (authData != null && authData.second.getPassword() != null) {
      String password = authData.second.getPassword();
      myDataProvider = authData.first;
      myPassword = password;
      LOG.debug("askPassword. dataProvider=" + getCurrentDataProviderName() + ", unifiedUrl= " + getUnifiedUrl(url) +
                ", login=" + authData.second.getLogin() + ", passwordKnown=" + (password != null));
      return password;
    }

    myPasswordKey = getUnifiedUrl(url);
    String password = CredentialPromptDialog.askPassword(myProject, myTitle, "Password for " + getDisplayableUrl(url),
                                                         CredentialAttributes(PASS_REQUESTER, myPasswordKey));
    LOG.debug("askPassword. Password was asked and returned: " + (password == null ? "NULL" : password.isEmpty() ? "EMPTY" : "NOT EMPTY"));
    if (password == null) {
      myWasCancelled = true;
      return "";
    }
    // Password is stored in the safe in PasswordSafePromptDialog.askPassword,
    // but it is not the right behavior (incorrect password is stored too because of that) and should be fixed separately.
    // We store it here manually, to let it work after that behavior is fixed.
    myPassword = password;
    myDataProvider = new GitDefaultHttpAuthDataProvider(); // workaround: askPassword remembers the password even it is not correct
    return password;
  }

  @Override
  @NotNull
  public String askUsername(@NotNull String url) {
    myUnifiedUrl = getUnifiedUrl(url);
    Pair<GitHttpAuthDataProvider, AuthData> authData = findBestAuthData(getUnifiedUrl(url));
    String login = null;
    String password = null;
    if (authData != null) {
      login = authData.second.getLogin();
      password = authData.second.getPassword();
      myDataProvider = authData.first;
    }
    LOG.debug("askUsername. dataProvider=" + getCurrentDataProviderName() + ", unifiedUrl= " + getUnifiedUrl(url) +
              ", login=" + login + ", passwordKnown=" + (password != null));
    if (login != null && password != null) {
      myPassword = password;
      return login;
    }

    AuthDialog dialog = showAuthDialog(getDisplayableUrl(url), login);
    LOG.debug("askUsername. Showed dialog:" + (dialog == null ? "NULL" : dialog.isOK() ? "OK" : "Cancel"));
    if (dialog == null || !dialog.isOK()) {
      myWasCancelled = true;
      return "";
    }

    // remember values to store in the database afterwards, if authentication succeeds
    myPassword = dialog.getPassword();
    myLogin = dialog.getUsername();
    mySaveOnDisk = dialog.isRememberPassword();
    myPasswordKey = makeKey(myUnifiedUrl, myLogin);

    return myLogin;
  }

  @Nullable
  private AuthDialog showAuthDialog(final String url, final String login) {
    final Ref<AuthDialog> dialog = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        dialog.set(new AuthDialog(myProject, myTitle, "Enter credentials for " + url, login, null, true));
        dialog.get().show();
      }
    }, ModalityState.any());
    return dialog.get();
  }

  @Override
  public void saveAuthData() {
    // save login and url
    if (myUnifiedUrl != null && myLogin != null) {
      GitRememberedInputs.getInstance().addUrl(myUnifiedUrl, myLogin);
    }

    // save password
    if (myPasswordKey != null && myPassword != null) {
      Credentials credentials = new Credentials(myPasswordKey, myPassword);
      PasswordSafe.getInstance().set(CredentialAttributes(PASS_REQUESTER, credentials.getUserName()), credentials, !mySaveOnDisk);
    }
  }

  @Override
  public void forgetPassword() {
    LOG.debug("forgetPassword. dataProvider=" + getCurrentDataProviderName() + ", unifiedUrl=" + myUnifiedUrl);
    if (myDataProvider != null && myUnifiedUrl != null) {
      myDataProvider.forgetPassword(myUnifiedUrl);
    }
  }

  @Nullable
  private String getCurrentDataProviderName() {
    return myDataProvider == null ? null : myDataProvider.getClass().getName();
  }

  @Override
  public boolean wasCancelled() {
    return myWasCancelled;
  }

  /**
   * Get the URL to display to the user in the authentication dialog.
   */
  @NotNull
  private String getDisplayableUrl(@Nullable String urlFromGit) {
    return !StringUtil.isEmptyOrSpaces(urlFromGit) ? urlFromGit : findPresetHttpUrl();
  }

  /**
   * Get the URL to be used as the authentication data identifier in the password safe and the settings.
   */
  @NotNull
  private String getUnifiedUrl(@Nullable String urlFromGit) {
    return changeHttpsToHttp(StringUtil.isEmptyOrSpaces(urlFromGit) ? findPresetHttpUrl() : urlFromGit);
  }

  @NotNull
  private String findPresetHttpUrl() {
    return ObjectUtils.chooseNotNull(ContainerUtil.find(myUrlsFromCommand, url -> {
      String scheme = UriUtil.splitScheme(url).getFirst();
      return scheme.startsWith("http");
    }), ContainerUtil.getFirstItem(myUrlsFromCommand));
  }

  /**
   * If the url scheme is HTTPS, store it as HTTP in the database, not to make user enter and remember same credentials twice.
   */
  @NotNull
  private static String changeHttpsToHttp(@NotNull String url) {
    String prefix = "https";
    if (url.startsWith(prefix)) {
      return "http" + url.substring(prefix.length());
    }
    return url;
  }

  // return the first that knows username + password; otherwise return the first that knows just the username
  @Nullable
  private Pair<GitHttpAuthDataProvider, AuthData> findBestAuthData(@NotNull String url) {
    Pair<GitHttpAuthDataProvider, AuthData> candidate = null;
    for (GitHttpAuthDataProvider provider : getProviders()) {
      AuthData data = provider.getAuthData(url);
      if (data != null) {
        Pair<GitHttpAuthDataProvider, AuthData> pair = Pair.create(provider, data);
        if (data.getPassword() != null) {
          return pair;
        }
        if (candidate == null) {
          candidate = pair;
        }
      }
    }
    return candidate;
  }
  
  @NotNull
  private List<GitHttpAuthDataProvider> getProviders() {
    List<GitHttpAuthDataProvider> providers = ContainerUtil.newArrayList();
    providers.add(new GitDefaultHttpAuthDataProvider());
    providers.addAll(Arrays.asList(GitHttpAuthDataProvider.EP_NAME.getExtensions()));
    return providers;
  }

  /**
   * Makes the password database key for the URL: inserts the login after the scheme: http://login@url.
   */
  @NotNull
  private static String makeKey(@NotNull String url, @Nullable String login) {
    if (login == null) {
      return url;
    }
    Couple<String> pair = UriUtil.splitScheme(url);
    String scheme = pair.getFirst();
    if (!StringUtil.isEmpty(scheme)) {
      return scheme + URLUtil.SCHEME_SEPARATOR + login + "@" + pair.getSecond();
    }
    return login + "@" + url;
  }

  public class GitDefaultHttpAuthDataProvider implements GitHttpAuthDataProvider {

    @Nullable
    @Override
    public AuthData getAuthData(@NotNull String url) {
      String userName = getUsername(url);
      String key = makeKey(url, userName);
      final PasswordSafe passwordSafe = PasswordSafe.getInstance();
      String password = passwordSafe.getPassword(PASS_REQUESTER, key);
      return new AuthData(StringUtil.notNullize(userName), password);
    }

    @Nullable
    private String getUsername(@NotNull String url) {
      return GitRememberedInputs.getInstance().getUserNameForUrl(url);
    }

    @Override
    public void forgetPassword(@NotNull String url) {
      String key = myPasswordKey != null ? myPasswordKey : makeKey(url, getUsername(url));
      LOG.debug("forgetPassword. key=" + key);
      PasswordSafe.getInstance().setPassword(PASS_REQUESTER, key, null);
    }
  }
}
