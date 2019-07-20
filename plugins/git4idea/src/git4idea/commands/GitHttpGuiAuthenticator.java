// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import git4idea.DialogManager;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.GitRememberedInputs;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Function;

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
 */
class GitHttpGuiAuthenticator implements GitHttpAuthenticator {

  private static final Logger LOG = Logger.getInstance(GitHttpGuiAuthenticator.class);
  private static final Class<GitHttpAuthenticator> PASS_REQUESTER = GitHttpAuthenticator.class;
  private static final String HTTP_SCHEME_URL_PREFIX = "http" + URLUtil.SCHEME_SEPARATOR;

  @NotNull private final Project myProject;
  @Nullable private final String myPresetUrl; //taken from GitHandler, used if git does not provide url
  @NotNull private final File myWorkingDirectory;
  @NotNull private final GitAuthenticationGate myAuthenticationGate;
  @NotNull private final GitAuthenticationMode myAuthenticationMode;

  @Nullable private ProviderAndData myProviderAndData = null;

  private boolean myCredentialHelperShouldBeUsed = false;

  GitHttpGuiAuthenticator(@NotNull Project project,
                          @NotNull Collection<String> urls,
                          @NotNull File workingDirectory,
                          @NotNull GitAuthenticationGate authenticationGate,
                          @NotNull GitAuthenticationMode authenticationMode) {
    myProject = project;
    myPresetUrl = findFirstHttpUrl(urls);
    myWorkingDirectory = workingDirectory;
    myAuthenticationGate = authenticationGate;
    myAuthenticationMode = authenticationMode;
  }

  @Nullable
  private static String findFirstHttpUrl(@NotNull Collection<String> urls) {
    return ContainerUtil.find(urls, url -> UriUtil.splitScheme(url).getFirst().startsWith("http"));
  }

  /**
   * At this point either {@link #myProviderAndData} is filled in {@link #askUsername(String)} or username is contained in url
   */
  @Override
  @NotNull
  public String askPassword(@NotNull String url) {
    if (myProviderAndData != null) {
      LOG.debug("askPassword. Data already filled in askUsername.");
      return myProviderAndData.getPassword();
    }

    Couple<String> usernameAndUrl = splitToUsernameAndUnifiedUrl(getRequiredUrl(url));
    // should be provided in URL or in askUsername together with password
    final String login = usernameAndUrl.first;
    if (login == null) {
      LOG.debug("askPassword. login unknown, cannot determine password without login");
      return "";
    }
    String unifiedUrl = usernameAndUrl.second;
    LOG.debug("askPassword. gitUrl=" + url + ", unifiedUrl=" + unifiedUrl);

    myProviderAndData = acquireData(unifiedUrl, provider -> provider.getDataForKnownLogin(login));

    if (myProviderAndData != null) {
      LOG.debug("askPassword. " + myProviderAndData.toString());
      return myProviderAndData.getPassword();
    }
    else {
      LOG.debug("askPassword. no data provided");
      return "";
    }
  }

  @Override
  @NotNull
  public String askUsername(@NotNull String url) {
    String unifiedUrl = splitToUsernameAndUnifiedUrl(getRequiredUrl(url)).second;
    LOG.debug("askUsername. gitUrl=" + url + ", unifiedUrl=" + unifiedUrl);

    myProviderAndData = acquireData(unifiedUrl, AuthDataProvider::getData);

    if (myProviderAndData != null) {
      LOG.debug("askUsername. " + myProviderAndData.toString());
      return myProviderAndData.getLogin();
    }
    else {
      LOG.debug("askUsername. no data provided");
      return "";
    }
  }

  @Nullable
  private ProviderAndData acquireData(@NotNull String unifiedUrl, @NotNull Function<? super AuthDataProvider, ? extends AuthData> dataAcquirer) {
    return myAuthenticationGate.waitAndCompute(() -> {
      try {
        for (AuthDataProvider provider : getProviders(unifiedUrl)) {
          AuthData data = dataAcquirer.apply(provider);
          if (data != null && data.getPassword() != null) {
            return new ProviderAndData(provider, data.getLogin(), data.getPassword());
          }
        }
        return null;
      }
      catch (ProcessCanceledException pce) {
        myAuthenticationGate.cancel();
        return new ProviderAndData(new CancelledProvider(unifiedUrl), "", "");
      }
      catch (CredentialHelperShouldBeUsedException e) {
        myAuthenticationGate.cancel();
        myCredentialHelperShouldBeUsed = true;
        return null;
      }
    });
  }

  static class CredentialHelperShouldBeUsedException extends RuntimeException implements ControlFlowException {
  }


  @NotNull
  private List<AuthDataProvider> getProviders(@NotNull String unifiedUrl) {
    List<AuthDataProvider> delegates = new ArrayList<>();
    PasswordSafeProvider passwordSafeProvider =
      new PasswordSafeProvider(unifiedUrl, GitRememberedInputs.getInstance(), PasswordSafe.getInstance());

    boolean showActionForGitHelper =  GitConfigUtil.isCredentialHelperUsed(myProject, myWorkingDirectory);

    DialogProvider dialogProvider = new DialogProvider(unifiedUrl, myProject, passwordSafeProvider, showActionForGitHelper);

    if (myAuthenticationMode != GitAuthenticationMode.NONE) {
      delegates.add(passwordSafeProvider);
    }
    if (myAuthenticationMode == GitAuthenticationMode.FULL) {
      delegates.addAll(ContainerUtil.map(GitHttpAuthDataProvider.EP_NAME.getExtensions(),
                                         (provider) -> new ExtensionAdapterProvider(unifiedUrl, myProject, provider)));
      delegates.add(dialogProvider);
    }
    return delegates;
  }

  @Override
  public void saveAuthData() {
    if (myProviderAndData == null) return;

    LOG.debug("saveAuthData. " + myProviderAndData.toString());
    myProviderAndData.getProvider().onAuthSuccess();
  }

  @Override
  public void forgetPassword() {
    if (myProviderAndData == null) return;

    LOG.debug("forgetPassword. " + myProviderAndData.toString());
    myProviderAndData.getProvider().onAuthFailure();
  }

  @Override
  public boolean wasCancelled() {
    if (myCredentialHelperShouldBeUsed) {
      return false;
    }
    return myProviderAndData != null && myProviderAndData.getProvider() instanceof CancelledProvider;
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

  private static abstract class AuthDataProvider {
    @NotNull protected final String myUrl;

    protected AuthDataProvider(@NotNull String url) {
      myUrl = url;
    }

    @NotNull
    abstract String getName();

    @Nullable
    abstract AuthData getData();

    @Nullable
    abstract AuthData getDataForKnownLogin(@NotNull String login);

    abstract void onAuthSuccess();

    abstract void onAuthFailure();
  }

  private static class ExtensionAdapterProvider extends AuthDataProvider {
    @NotNull private final Project myProject;
    @NotNull private final GitHttpAuthDataProvider myDelegate;

    private AuthData myData = null;

    protected ExtensionAdapterProvider(@NotNull String url, @NotNull Project project, @NotNull GitHttpAuthDataProvider provider) {
      super(url);
      myProject = project;
      myDelegate = provider;
    }

    @NotNull
    @Override
    public String getName() {
      return myDelegate.getClass().getName();
    }

    @Override
    public AuthData getData() {
      return (myData = myDelegate.getAuthData(myProject, myUrl));
    }

    @Override
    public AuthData getDataForKnownLogin(@NotNull String login) {
      return (myData = myDelegate.getAuthData(myProject, myUrl, login));
    }

    @Override
    public void onAuthSuccess() {}

    @Override
    public void onAuthFailure() {
      if (myData != null) myDelegate.forgetPassword(myProject, myUrl, myData);
    }
  }

  private static class DialogProvider extends AuthDataProvider {
    @NotNull private final Project myProject;
    @NotNull private final PasswordSafeProvider myPasswordSafeDelegate;
    private final boolean showActionForGitHelper;
    private boolean myCancelled;
    private boolean myDataForSession = false;

    protected DialogProvider(@NotNull String url,
                             @NotNull Project project,
                             @NotNull PasswordSafeProvider passwordSafeDelegate,
                             boolean showActionForGitHelper) {
      super(url);
      myProject = project;
      myPasswordSafeDelegate = passwordSafeDelegate;
      this.showActionForGitHelper = showActionForGitHelper;
    }

    @NotNull
    @Override
    public String getName() {
      return myDataForSession ? "Session Provider" : "Dialog";
    }

    @Override
    @Nullable
    public AuthData getData() {
      return getDataFromDialog(myUrl, myPasswordSafeDelegate.getRememberedLogin(myUrl), true);
    }

    @Override
    @Nullable
    public AuthData getDataForKnownLogin(@NotNull String login) {
      return getDataFromDialog(myUrl, login, false);
    }

    @Override
    public void onAuthSuccess() {
      if (myDataForSession) return;
      myPasswordSafeDelegate.onAuthSuccess();
    }

    @Override
    public void onAuthFailure() {
      if (myDataForSession) return;
      myPasswordSafeDelegate.onAuthFailure();
    }

    @NotNull
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
        if (dialog.getExitCode() == GitHttpLoginDialog.USE_CREDENTIAL_HELPER_CODE) {
          LOG.debug("Credential helper is enabled");
          GitVcsApplicationSettings.getInstance().setUseCredentialHelper(true);
          throw new CredentialHelperShouldBeUsedException();
        }
        throw new ProcessCanceledException();
      }
      myPasswordSafeDelegate.setRememberPassword(dialog.getRememberPassword());

      AuthData sessionAuthData = dialog.getExternalAuthData();
      if (sessionAuthData != null) {
        myDataForSession = true;
        return sessionAuthData;
      }

      AuthData authData = new AuthData(dialog.getUsername(), dialog.getPassword());
      myPasswordSafeDelegate.setData(authData);
      myPasswordSafeDelegate.savePassword(url);
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
          new GitHttpLoginDialog(myProject, url, myPasswordSafeDelegate.isRememberPasswordByDefault(), username, editableUsername,
                                 showActionForGitHelper);
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

  @VisibleForTesting
  static class PasswordSafeProvider extends AuthDataProvider {
    @NotNull private final DvcsRememberedInputs myRememberedInputs;
    @NotNull private final PasswordSafe myPasswordSafe;

    private AuthData myData;

    private boolean mySavePassword = false;

    PasswordSafeProvider(@NotNull String url, @NotNull DvcsRememberedInputs gitRememberedInputs, @NotNull PasswordSafe passwordSafe) {
      super(url);
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
    public AuthData getData() {
      String rememberedLogin = getRememberedLogin(myUrl);
      if (rememberedLogin == null) return null;
      return getDataForKnownLogin(rememberedLogin);
    }

    @Nullable
    public String getRememberedLogin(@NotNull String url) {
      return myRememberedInputs.getUserNameForUrl(url);
    }

    @Override
    @Nullable
    public AuthData getDataForKnownLogin(@NotNull String login) {
      String key = makeKey(myUrl, login);
      Credentials credentials = CredentialAttributesKt.getAndMigrateCredentials(oldCredentialAttributes(key), credentialAttributes(key));
      String password = StringUtil.nullize(credentials == null ? null : credentials.getPasswordAsString());
      return (myData = new AuthData(login, password));
    }

    @Override
    public void onAuthSuccess() {
    }

    @Override
    public void onAuthFailure() {
      if (myData == null) return;
      String key = makeKey(myUrl, myData.getLogin());
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

    public void savePassword(@NotNull String url) {
      if (myData == null || myData.getPassword() == null) return;
      myRememberedInputs.addUrl(url, myData.getLogin());
      if (!mySavePassword) return;
      String key = makeKey(url, myData.getLogin());
      Credentials credentials = new Credentials(key, myData.getPassword());
      myPasswordSafe.set(credentialAttributes(key), credentials);
    }

    @VisibleForTesting
    @NotNull
    static CredentialAttributes credentialAttributes(@NotNull String key) {
      return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Git HTTP", key), key, PASS_REQUESTER);
    }

    @NotNull
    private static CredentialAttributes oldCredentialAttributes(@NotNull String key) {
      return CredentialAttributesKt.CredentialAttributes(PASS_REQUESTER, key);
    }

    /**
     * Makes the password database key for the URL: inserts the login after the scheme: http://login@url.
     */
    @VisibleForTesting
    @NotNull
    static String makeKey(@NotNull String url, @Nullable String login) {
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

  private static class CancelledProvider extends AuthDataProvider {
    CancelledProvider(@NotNull String url) {
      super(url);
    }

    @NotNull
    @Override
    String getName() {
      return "Cancelled";
    }

    @Nullable
    @Override
    AuthData getData() {
      return new AuthData("", "");
    }

    @Nullable
    @Override
    AuthData getDataForKnownLogin(@NotNull String login) {
      return new AuthData("", "");
    }

    @Override
    void onAuthSuccess() {}

    @Override
    void onAuthFailure() {}
  }

  private static class ProviderAndData {
    @NotNull private final AuthDataProvider myProvider;
    @NotNull private final String myLogin;
    @NotNull private final String myPassword;

    private ProviderAndData(@NotNull AuthDataProvider provider, @NotNull String login, @NotNull String password) {
      myProvider = provider;
      myLogin = login;
      myPassword = password;
    }

    @NotNull
    private AuthDataProvider getProvider() {
      return myProvider;
    }

    @NotNull
    private String getLogin() {
      return myLogin;
    }

    @NotNull
    private String getPassword() {
      return myPassword;
    }

    @Override
    public String toString() {
      return "provider=\'" + myProvider.getName() + "\', login='" + myLogin + '\'';
    }
  }
}
