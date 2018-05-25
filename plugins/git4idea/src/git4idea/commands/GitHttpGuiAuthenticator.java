// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.intellij.util.ObjectUtils;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import git4idea.DialogManager;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.GitRememberedInputs;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * <p>Handles "ask username" and "ask password" requests from Git:
 * shows authentication dialog in the GUI, waits for user input and returns the credentials supplied by the user.</p>
 * <p>If user cancels the dialog, empty string is returned.</p>
 * <p>If no username is specified in the URL, Git queries for the username and for the password consecutively.
 * In this case to avoid showing dialogs twice, the component asks for both credentials at once,
 * and remembers the password to provide it to the Git process during the next request without requiring user interaction.</p>
 * <p>New instance of the  {@link GitHttpGuiAuthenticator} should be created for each session, i. e. for each remote operation call.</p>
 * <p>
 * git version <=1.7.7 does not provide url for methods (method parameter will be a blank line)
 *
 * @author Kirill Likhodedov
 */
class GitHttpGuiAuthenticator implements GitHttpAuthenticator {

  private static final Logger LOG = Logger.getInstance(GitHttpGuiAuthenticator.class);
  private static final Class<GitHttpAuthenticator> PASS_REQUESTER = GitHttpAuthenticator.class;
  public static final String HTTP_SCHEME_URL_PREFIX = "http" + URLUtil.SCHEME_SEPARATOR;

  @NotNull private final Project myProject;
  @Nullable private final String myPresetUrl; //taken from GitHandler, used if git does not provide url

  private String myUnifiedUrl = null; //remote url with http schema and no username
  private String myPassword = null;

  @NotNull private final PasswordSafeProvider myPasswordSafeProvider;
  @NotNull private final DialogProvider myDialogProvider;
  @NotNull private AuthDataProvider myCurrentProvider;

  GitHttpGuiAuthenticator(@NotNull Project project, @NotNull Collection<String> urls) {
    myProject = project;
    myPresetUrl = findFirstHttpUrl(urls);

    myPasswordSafeProvider = new PasswordSafeProvider(GitRememberedInputs.getInstance(), PasswordSafe.getInstance());
    myDialogProvider = new DialogProvider(myProject, myPasswordSafeProvider);
    myCurrentProvider = myPasswordSafeProvider;
  }

  @Nullable
  private static String findFirstHttpUrl(@NotNull Collection<String> urls) {
    return ContainerUtil.find(urls, url -> UriUtil.splitScheme(url).getFirst().startsWith("http"));
  }

  /**
   * At this point either {@link #myPassword} is filled in {@link #askUsername(String)} or username is contained in url
   */
  @Override
  @NotNull
  public String askPassword(@NotNull String url) {
    if (wasCancelled()) {
      LOG.debug("askPassword. url=" + url + ", cancelled in askUsername");
      return "";
    }

    if (myPassword != null) {
      LOG.debug("askPassword. Data already filled in askUsername.");
      return ObjectUtils.assertNotNull(myPassword);
    }

    Couple<String> usernameAndUrl = splitToUsernameAndUnifiedUrl(getRequiredUrl(url));
    myUnifiedUrl = usernameAndUrl.second;
    LOG.debug("askPassword. gitUrl=" + url + ", unifiedUrl=" + myUnifiedUrl);

    String login = usernameAndUrl.first;
    if (login == null) {
      LOG.warn("askPassword. Could not find username");
      return "";
    }
    String password = null;
    for (AuthDataProvider delegate : getProviders()) {
      AuthData data = delegate.getDataForUser(myUnifiedUrl, login);
      if (data != null && data.getPassword() != null) {
        password = data.getPassword();
        myCurrentProvider = delegate;
        break;
      }
    }
    LOG.debug("askPassword. provider=" + myCurrentProvider.getName() + ", login=" + login + ", passwordKnown=" + (password != null));
    return StringUtil.notNullize(password);
  }

  @Override
  @NotNull
  public String askUsername(@NotNull String url) {
    myUnifiedUrl = splitToUsernameAndUnifiedUrl(getRequiredUrl(url)).second;
    LOG.debug("askUsername. gitUrl=" + url + ", unifiedUrl=" + myUnifiedUrl);

    String login = null;
    String password = null;
    for (AuthDataProvider provider : getProviders()) {
      AuthData data = provider.getData(myUnifiedUrl, login);
      if (data != null) {
        if (login == null) {
          login = data.getLogin();
          myCurrentProvider = provider;
        }
        if (data.getPassword() != null) {
          login = data.getLogin();
          password = data.getPassword();
          myCurrentProvider = provider;
          break;
        }
      }
    }
    LOG.debug("askUsername. provider=" + myCurrentProvider.getName() + ", login=" + login + ", passwordKnown=" + (password != null));
    if (login != null && password != null) myPassword = password;
    return StringUtil.notNullize(login);
  }

  @NotNull
  private List<AuthDataProvider> getProviders() {
    List<AuthDataProvider> delegates = new ArrayList<>();
    delegates.add(myPasswordSafeProvider);
    delegates.addAll(ContainerUtil.map(GitHttpAuthDataProvider.EP_NAME.getExtensions(),
                                       (provider) -> new ExtensionAdapterProvider(myProject, provider)));
    delegates.add(myDialogProvider);
    return delegates;
  }

  @Override
  public void saveAuthData() {
    if (myUnifiedUrl == null) return;

    LOG.debug("saveAuthData. provider=" + myCurrentProvider.getName() + ", unifiedUrl=" + myUnifiedUrl);
    myCurrentProvider.onAuthSuccess(myUnifiedUrl);
  }

  @Override
  public void forgetPassword() {
    if (myUnifiedUrl == null) return;

    LOG.debug("forgetPassword. provider=" + myCurrentProvider.getName() + ", unifiedUrl=" + myUnifiedUrl);
    myCurrentProvider.onAuthFailure(myUnifiedUrl);
  }

  @Override
  public boolean wasCancelled() {
    return myDialogProvider.isCancelled();
  }

  /**
   * Get the URL to be used as the authentication data identifier in the password safe and the settings.
   * git <=1.7.7 does not provide url so we have to pass it via handler
   */
  @NotNull
  private String getRequiredUrl(@Nullable String urlFromGit) {
    if (urlFromGit != null && !StringUtil.isEmptyOrSpaces(urlFromGit)) {
      return urlFromGit;
    }
    else {
      if (myPresetUrl == null) throw new IllegalStateException("Invalid remote urls in handler");
      return myPresetUrl;
    }
  }

  /**
   * Split url to a pair of username and http-schemed url without username
   *
   * @return nullable username and non-nullable url
   */
  @NotNull
  private static Couple<String> splitToUsernameAndUnifiedUrl(@NotNull String url) {
    if (StringUtil.isEmptyOrSpaces(url)) return Couple.of(null, url);

    Couple<String> couple = UriUtil.splitScheme(url);
    String urlWithoutScheme = couple.second;
    if (StringUtil.isEmptyOrSpaces(urlWithoutScheme)) return Couple.of(null, url);

    int i = urlWithoutScheme.indexOf('@');
    if (i <= 0) {
      return Couple.of(null, HTTP_SCHEME_URL_PREFIX + urlWithoutScheme);
    }
    else {
      return Couple.of(urlWithoutScheme.substring(0, i), HTTP_SCHEME_URL_PREFIX + urlWithoutScheme.substring(i + 1));
    }
  }

  private interface AuthDataProvider {
    @NotNull
    String getName();

    @Nullable
    AuthData getData(@NotNull String url, @Nullable String login);

    @Nullable
    AuthData getDataForUser(@NotNull String url, @NotNull String login);

    void onAuthSuccess(@NotNull String url);

    void onAuthFailure(@NotNull String url);
  }

  private static class ExtensionAdapterProvider implements AuthDataProvider {
    @NotNull private final Project myProject;
    @NotNull private final GitHttpAuthDataProvider myDelegate;

    private AuthData myData = null;

    public ExtensionAdapterProvider(@NotNull Project project, @NotNull GitHttpAuthDataProvider provider) {
      myProject = project;
      myDelegate = provider;
    }

    @NotNull
    @Override
    public String getName() {
      return myDelegate.getClass().getName();
    }

    @Override
    public AuthData getData(@NotNull String url, @Nullable String suggestedLogin) {
      return (myData = myDelegate.getAuthData(myProject, url));
    }

    @Override
    public AuthData getDataForUser(@NotNull String url, @NotNull String login) {
      return (myData = myDelegate.getAuthData(myProject, url, login));
    }

    @Override
    public void onAuthSuccess(@NotNull String url) {}

    @Override
    public void onAuthFailure(@NotNull String url) {
      if (myData != null) myDelegate.forgetPassword(url, myData);
    }
  }

  private static class DialogProvider implements AuthDataProvider {
    @NotNull private final Project myProject;
    @NotNull private final PasswordSafeProvider myPasswordSafeDelegate;
    private boolean myCancelled;
    private boolean myDataForSession = false;

    public DialogProvider(@NotNull Project project, @NotNull PasswordSafeProvider passwordSafeDelegate) {
      myProject = project;
      myPasswordSafeDelegate = passwordSafeDelegate;
    }

    @NotNull
    @Override
    public String getName() {
      return myDataForSession ? "Session Provider" : "Dialog";
    }

    @Override
    @Nullable
    public AuthData getData(@NotNull String url, @Nullable String login) {
      return getDataFromDialog(url, login, true);
    }

    @Override
    @Nullable
    public AuthData getDataForUser(@NotNull String url, @NotNull String login) {
      return getDataFromDialog(url, login, false);
    }

    @Override
    public void onAuthSuccess(@NotNull String url) {
      if (myDataForSession) return;
      myPasswordSafeDelegate.onAuthSuccess(url);
    }

    @Override
    public void onAuthFailure(@NotNull String url) {
      if (myDataForSession) return;
      myPasswordSafeDelegate.onAuthFailure(url);
    }

    @Nullable
    private AuthData getDataFromDialog(@NotNull String url, @Nullable String username, boolean editableUsername) {
      Map<String, InteractiveGitHttpAuthDataProvider> providers = new HashMap<>();
      for (GitRepositoryHostingService service : GitRepositoryHostingService.EP_NAME.getExtensions()) {
        InteractiveGitHttpAuthDataProvider provider = editableUsername || username == null
                                                      ? service.getInteractiveAuthDataProvider(myProject, url)
                                                      : service.getInteractiveAuthDataProvider(myProject, url, username);

        if (provider != null) providers.put(service.getServiceDisplayName(), provider);
      }
      GitHttpLoginDialog dialog = showAuthDialog(UriUtil.splitScheme(url).second, username, editableUsername, providers);
      LOG.debug("Showed dialog:" + (dialog.isOK() ? "OK" : "Cancel"));
      if (!dialog.isOK()) {
        myCancelled = true;
        return null;
      }
      myPasswordSafeDelegate.setRememberPassword(dialog.getRememberPassword());

      AuthData sessionAuthData = dialog.getExternalAuthData();
      if (sessionAuthData != null) {
        myDataForSession = true;
        return sessionAuthData;
      }

      AuthData authData = new AuthData(dialog.getUsername(), dialog.getPassword());
      myPasswordSafeDelegate.setData(authData);
      return authData;
    }

    @NotNull
    private GitHttpLoginDialog showAuthDialog(@NotNull String url,
                                              @Nullable String username,
                                              boolean editableUsername,
                                              @NotNull Map<String, ? extends InteractiveGitHttpAuthDataProvider> interactiveProviders) {
      Ref<GitHttpLoginDialog> dialogRef = Ref.create();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        GitHttpLoginDialog dialog =
          new GitHttpLoginDialog(myProject, url, myPasswordSafeDelegate.isRememberPasswordByDefault(), username, editableUsername);
        dialog.setInteractiveDataProviders(interactiveProviders);
        dialogRef.set(dialog);
        DialogManager.show(dialog);
      }, ModalityState.any());
      return dialogRef.get();
    }

    public boolean isCancelled() {
      return myCancelled;
    }
  }

  private static class PasswordSafeProvider implements AuthDataProvider {
    @NotNull private final DvcsRememberedInputs myRememberedInputs;
    @NotNull private final PasswordSafe myPasswordSafe;

    private AuthData myData;

    private boolean mySavePassword = false;

    public PasswordSafeProvider(@NotNull DvcsRememberedInputs gitRememberedInputs, @NotNull PasswordSafe passwordSafe) {
      myRememberedInputs = gitRememberedInputs;
      myPasswordSafe = passwordSafe;
    }

    @NotNull
    @Override
    public String getName() {
      return "Password Safe";
    }

    @Override
    @Nullable
    public AuthData getData(@NotNull String url, @Nullable String login) {
      if (login == null) {
        String rememberedLogin = myRememberedInputs.getUserNameForUrl(url);
        if (rememberedLogin == null) {
          return null;
        }
        return getDataForUser(url, rememberedLogin);
      }
      return getDataForUser(url, login);
    }

    @Override
    @Nullable
    public AuthData getDataForUser(@NotNull String url, @NotNull String login) {
      String key = makeKey(url, login);
      Credentials credentials = CredentialAttributesKt.getAndMigrateCredentials(oldCredentialAttributes(key), credentialAttributes(key));
      String password = StringUtil.nullize(credentials == null ? null : credentials.getPasswordAsString());
      return new AuthData(login, password);
    }

    @Override
    public void onAuthSuccess(@NotNull String url) {
      if (myData == null || myData.getPassword() == null) return;
      myRememberedInputs.addUrl(url, myData.getLogin());
      if (!mySavePassword) return;
      String key = makeKey(url, myData.getLogin());
      Credentials credentials = new Credentials(key, myData.getPassword());
      myPasswordSafe.set(credentialAttributes(key), credentials);
    }

    @Override
    public void onAuthFailure(@NotNull String url) {
      if (myData == null) return;
      String key = makeKey(url, myData.getLogin());
      CredentialAttributes attributes = credentialAttributes(key);
      LOG.debug("forgetPassword. key=" + attributes.getUserName());
      myPasswordSafe.set(attributes, null);
    }

    public void setData(@NotNull AuthData data) {
      myData = data;
    }

    public void setRememberPassword(boolean remember) {
      mySavePassword = remember;
      myPasswordSafe.setRememberPasswordByDefault(remember);
    }

    public boolean isRememberPasswordByDefault() {
      return myPasswordSafe.isRememberPasswordByDefault();
    }

    @NotNull
    private static CredentialAttributes credentialAttributes(@NotNull String key) {
      return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Git HTTP", key), key, PASS_REQUESTER);
    }

    @NotNull
    private static CredentialAttributes oldCredentialAttributes(@NotNull String key) {
      return CredentialAttributesKt.CredentialAttributes(PASS_REQUESTER, key);
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
  }
}
