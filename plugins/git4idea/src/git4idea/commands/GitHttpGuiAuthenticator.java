// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.externalProcessAuthHelper.AuthenticationGate;
import com.intellij.externalProcessAuthHelper.AuthenticationMode;
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
import git4idea.i18n.GitBundle;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.GitRememberedInputs;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
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
@ApiStatus.Internal
public final class GitHttpGuiAuthenticator implements GitHttpAuthenticator {

  private static final Logger LOG = Logger.getInstance(GitHttpGuiAuthenticator.class);
  private static final String HTTP_SCHEME_URL_PREFIX = "http" + URLUtil.SCHEME_SEPARATOR;

  private final @NotNull Project myProject;
  private final @Nullable String myPresetUrl; //taken from GitHandler, used if git does not provide url
  private final @NotNull File myWorkingDirectory;
  private final @NotNull AuthenticationGate myAuthenticationGate;
  private final @NotNull AuthenticationMode myAuthenticationMode;

  private boolean myWasRequested = false;
  private volatile @Nullable ProviderAndData myProviderAndData = null;
  private volatile boolean myCredentialHelperShouldBeUsed = false;

  @VisibleForTesting
  public GitHttpGuiAuthenticator(@NotNull Project project,
                                 @NotNull Collection<String> urls,
                                 @NotNull File workingDirectory,
                                 @NotNull AuthenticationGate authenticationGate,
                                 @NotNull AuthenticationMode authenticationMode) {
    myProject = project;
    myPresetUrl = findFirstHttpUrl(urls);
    myWorkingDirectory = workingDirectory;
    myAuthenticationGate = authenticationGate;
    myAuthenticationMode = authenticationMode;
  }

  private static @Nullable String findFirstHttpUrl(@NotNull Collection<String> urls) {
    return ContainerUtil.find(urls, url -> UriUtil.splitScheme(url).getFirst().startsWith("http"));
  }

  /**
   * At this point either {@link #myProviderAndData} is filled in {@link #askUsername(String)} or username is contained in url
   */
  @Override
  public @Nullable String askPassword(@NotNull String url) {
    myWasRequested = true;

    ProviderAndData providerAndData = myProviderAndData;
    if (providerAndData != null && providerAndData.myProvider instanceof CancelledProvider) {
      LOG.debug("askPassword. Auth was cancelled.");
      return null;
    }

    if (providerAndData != null) {
      LOG.debug("askPassword. Data already filled in askUsername.");
      return providerAndData.getPassword();
    }

    Couple<String> usernameAndUrl = splitToUsernameAndUrl(getRequiredUrl(url));
    // should be provided in URL or in askUsername together with password
    final String login = usernameAndUrl.first;
    if (login == null) {
      LOG.debug("askPassword. login unknown, cannot determine password without login");
      return "";
    }
    String urlWithoutUsername = usernameAndUrl.second;
    LOG.debug("askPassword. gitUrl=" + url + ", urlWithoutUsername=" + urlWithoutUsername);

    ProviderAndData newData = acquireData(urlWithoutUsername, provider -> provider.getDataForKnownLogin(login));
    myProviderAndData = newData;

    if (newData != null) {
      LOG.debug("askPassword. " + newData);
      return newData.getPassword();
    }
    else {
      LOG.debug("askPassword. no data provided");
      return "";
    }
  }

  @Override
  public @Nullable String askUsername(@NotNull String url) {
    myWasRequested = true;

    String urlWithoutUsername = splitToUsernameAndUrl(getRequiredUrl(url)).second;
    LOG.debug("askUsername. gitUrl=" + url + ", urlWithoutUsername=" + urlWithoutUsername);

    ProviderAndData providerAndData = acquireData(urlWithoutUsername, AuthDataProvider::getData);
    myProviderAndData = providerAndData;

    if (providerAndData != null && providerAndData.myProvider instanceof CancelledProvider) {
      LOG.debug("askUsername. Auth was cancelled.");
      return null;
    }

    if (providerAndData != null) {
      LOG.debug("askUsername. " + providerAndData);
      return providerAndData.getLogin();
    }
    else {
      LOG.debug("askUsername. no data provided");
      return "";
    }
  }

  private @Nullable ProviderAndData acquireData(@NotNull String urlWithoutUsername,
                                                @NotNull Function<? super AuthDataProvider, ? extends AuthData> dataAcquirer) {
    return myAuthenticationGate.waitAndCompute(() -> {
      for (AuthDataProvider provider : getProviders(urlWithoutUsername)) {
        try {
          AuthData data = dataAcquirer.apply(provider);
          if (data != null && data.getPassword() != null) {
            return new ProviderAndData(provider, data.getLogin(), data.getPassword());
          }
        }
        catch (ProcessCanceledException pce) {
          LOG.debug("Auth process cancelled by" + provider);
          myAuthenticationGate.cancel();
          return new ProviderAndData(new CancelledProvider(urlWithoutUsername), "", "");
        }
        catch (CredentialHelperShouldBeUsedException e) {
          LOG.debug("Auth switched to credential helper");
          myAuthenticationGate.cancel();
          myCredentialHelperShouldBeUsed = true;
          return null;
        }
        catch (Throwable e) {
          LOG.error("Can't get password from " + provider, e);
        }
      }
      return null;
    });
  }

  static final class CredentialHelperShouldBeUsedException extends RuntimeException implements ControlFlowException {
  }

  private @NotNull List<AuthDataProvider> getProviders(@NotNull String urlWithoutUsername) {
    List<AuthDataProvider> delegates = new ArrayList<>();
    PasswordSafeProvider passwordSafeProvider =
      new PasswordSafeProvider(urlWithoutUsername, GitRememberedInputs.getInstance(), PasswordSafe.getInstance());

    boolean showActionForGitHelper = GitConfigUtil.isCredentialHelperUsed(myProject, myWorkingDirectory);

    DialogProvider dialogProvider = new DialogProvider(urlWithoutUsername, myProject, passwordSafeProvider, showActionForGitHelper);

    if (myAuthenticationMode != AuthenticationMode.NONE) {
      delegates.add(passwordSafeProvider);
    }
    List<ExtensionAdapterProvider> extensionAdapterProviders =
      ContainerUtil.map(GitHttpAuthDataProvider.EP_NAME.getExtensionList(), (provider) -> {
        return new ExtensionAdapterProvider(urlWithoutUsername, myProject, provider);
      });
    if (myAuthenticationMode == AuthenticationMode.SILENT) {
      delegates.addAll(ContainerUtil.filter(extensionAdapterProviders, p -> p.myDelegate.isSilent()));
    }
    else if (myAuthenticationMode == AuthenticationMode.FULL) {
      delegates.addAll(extensionAdapterProviders);
      delegates.add(dialogProvider);
    }
    return delegates;
  }

  @Override
  public void saveAuthData() {
    ProviderAndData providerAndData = myProviderAndData;
    if (providerAndData == null) return;

    LOG.debug("saveAuthData. " + providerAndData.toString());
    providerAndData.getProvider().onAuthSuccess();
  }

  @Override
  public void forgetPassword() {
    ProviderAndData providerAndData = myProviderAndData;
    if (providerAndData == null) return;

    LOG.debug("forgetPassword. " + providerAndData.toString());
    providerAndData.getProvider().onAuthFailure();
  }

  @Override
  public boolean wasCancelled() {
    if (myCredentialHelperShouldBeUsed) {
      return false;
    }
    ProviderAndData providerAndData = myProviderAndData;
    return providerAndData != null && providerAndData.getProvider() instanceof CancelledProvider;
  }

  @Override
  public boolean wasRequested() {
    return myWasRequested;
  }

  /**
   * Get the URL to be used as the authentication data identifier in the password safe and the settings.
   * git <=1.7.7 does not provide url so we have to pass it via handler
   */
  private @NotNull String getRequiredUrl(@Nullable String urlFromGit) {
    if (urlFromGit != null && !StringUtil.isEmptyOrSpaces(urlFromGit)) {
      return urlFromGit;
    }
    else {
      if (myPresetUrl == null) throw new IllegalStateException("Invalid remote urls in handler");
      return myPresetUrl;
    }
  }

  /**
   * @return non-nullable http-schemed url
   */
  private static @NotNull String unifyUrl(@NotNull String url) {
    if (StringUtil.isEmptyOrSpaces(url)) return url;

    Couple<String> schemeAndUrl = UriUtil.splitScheme(url);
    String urlWithoutScheme = schemeAndUrl.second;
    if (StringUtil.isEmptyOrSpaces(urlWithoutScheme)) return url;

    return HTTP_SCHEME_URL_PREFIX + urlWithoutScheme;
  }

  /**
   * Split url to a pair of username and url without username
   *
   * @return nullable username and non-nullable url
   */
  private static @NotNull Couple<String> splitToUsernameAndUrl(@NotNull String url) {
    if (StringUtil.isEmptyOrSpaces(url)) return Couple.of(null, url);

    Couple<String> schemeAndUrl = UriUtil.splitScheme(url);
    String scheme = schemeAndUrl.first;
    String urlWithoutScheme = schemeAndUrl.second;
    if (StringUtil.isEmptyOrSpaces(urlWithoutScheme)) return Couple.of(null, url);

    int i = urlWithoutScheme.indexOf('@');
    if (i <= 0) {
      return Couple.of(null, scheme + URLUtil.SCHEME_SEPARATOR + urlWithoutScheme);
    }
    else {
      return Couple.of(urlWithoutScheme.substring(0, i), scheme + URLUtil.SCHEME_SEPARATOR + urlWithoutScheme.substring(i + 1));
    }
  }

  @ApiStatus.Internal
  public abstract static class AuthDataProvider {
    protected final @NotNull String myUrl;

    protected AuthDataProvider(@NotNull String url) {
      myUrl = url;
    }

    abstract @NonNls @NotNull String getName();

    abstract @Nullable AuthData getData();

    abstract @Nullable AuthData getDataForKnownLogin(@NotNull String login);

    abstract void onAuthSuccess();

    abstract void onAuthFailure();
  }

  private static final class ExtensionAdapterProvider extends AuthDataProvider {
    private final @NotNull Project myProject;
    private final @NotNull GitHttpAuthDataProvider myDelegate;

    private AuthData myData = null;

    ExtensionAdapterProvider(@NotNull String url, @NotNull Project project, @NotNull GitHttpAuthDataProvider provider) {
      super(url);
      myProject = project;
      myDelegate = provider;
    }

    @Override
    public @NotNull String getName() {
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
    public void onAuthSuccess() { }

    @Override
    public void onAuthFailure() {
      if (myData != null) myDelegate.forgetPassword(myProject, myUrl, myData);
    }

    @Override
    public String toString() {
      return "ExtensionAdapterProvider(" + myDelegate + ")";
    }
  }

  private static final class DialogProvider extends AuthDataProvider {
    private final @NotNull Project myProject;
    private final @NotNull PasswordSafeProvider myPasswordSafeDelegate;
    private final boolean showActionForGitHelper;
    private boolean myCancelled;
    private boolean myDataForSession = false;

    DialogProvider(@NotNull String url,
                             @NotNull Project project,
                             @NotNull PasswordSafeProvider passwordSafeDelegate,
                             boolean showActionForGitHelper) {
      super(url);
      myProject = project;
      myPasswordSafeDelegate = passwordSafeDelegate;
      this.showActionForGitHelper = showActionForGitHelper;
    }

    @Override
    public @NotNull String getName() {
      return myDataForSession ? "Session Provider" : "Dialog";
    }

    @Override
    public @NotNull AuthData getData() {
      return getDataFromDialog(myUrl, myPasswordSafeDelegate.getRememberedLogin(myUrl), true);
    }

    @Override
    public @NotNull AuthData getDataForKnownLogin(@NotNull String login) {
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

    private @NotNull AuthData getDataFromDialog(@NotNull String url, @Nullable String username, boolean editableUsername) {
      Map<String, InteractiveGitHttpAuthDataProvider> providers = new HashMap<>();
      for (GitRepositoryHostingService service : GitRepositoryHostingService.EP_NAME.getExtensionList()) {
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

    private @NotNull GitHttpLoginDialog showAuthDialog(@NotNull String url,
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
  @ApiStatus.Internal
  public static class PasswordSafeProvider extends AuthDataProvider {
    private final @NotNull DvcsRememberedInputs myRememberedInputs;
    private final @NotNull PasswordSafe myPasswordSafe;

    private AuthData myData;

    private boolean mySavePassword = false;

    PasswordSafeProvider(@NotNull String url, @NotNull DvcsRememberedInputs gitRememberedInputs, @NotNull PasswordSafe passwordSafe) {
      super(url);
      myRememberedInputs = gitRememberedInputs;
      myPasswordSafe = passwordSafe;
    }

    @Override
    public @NotNull String getName() {
      return "Password Safe";
    }

    @Override
    public @Nullable AuthData getData() {
      String rememberedLogin = getRememberedLogin(myUrl);
      if (rememberedLogin == null) return null;
      return getDataForKnownLogin(rememberedLogin);
    }

    public @Nullable String getRememberedLogin(@NotNull String url) {
      return myRememberedInputs.getUserNameForUrl(unifyUrl(url));
    }

    @Override
    public @Nullable AuthData getDataForKnownLogin(@NotNull String login) {
      String key = makeKey(myUrl, login);
      Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes(key));
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
      String unifiedUrl = unifyUrl(url);

      if (myData == null || myData.getPassword() == null) return;
      myRememberedInputs.addUrl(unifiedUrl, myData.getLogin());

      if (!mySavePassword) return;

      String key = makeKey(unifiedUrl, myData.getLogin());
      Credentials credentials = new Credentials(key, myData.getPassword());
      myPasswordSafe.set(credentialAttributes(key), credentials);
    }

    @VisibleForTesting
    @ApiStatus.Internal
    public static @NotNull CredentialAttributes credentialAttributes(@NotNull String key) {
      return new CredentialAttributes(
        CredentialAttributesKt.generateServiceName(GitBundle.message("label.credential.store.key.http.password"), key), key);
    }

    /**
     * Makes the password database key for the URL: inserts the login after the scheme: http://login@url.
     */
    @VisibleForTesting
    @ApiStatus.Internal
    public static @NotNull String makeKey(@NotNull String url, @Nullable String login) {
      if (login == null) {
        return url;
      }
      String unifiedUrl = unifyUrl(url);
      Couple<String> pair = UriUtil.splitScheme(unifiedUrl);
      String scheme = pair.getFirst();
      if (!StringUtil.isEmpty(scheme)) {
        return scheme + URLUtil.SCHEME_SEPARATOR + login + "@" + pair.getSecond();
      }
      return login + "@" + url;
    }
  }

  private static final class CancelledProvider extends AuthDataProvider {
    CancelledProvider(@NotNull String url) {
      super(url);
    }

    @NotNull
    @Override
    String getName() {
      return "Cancelled";
    }

    @Override
    @NotNull
    AuthData getData() {
      return new AuthData("", "");
    }

    @Override
    @NotNull
    AuthData getDataForKnownLogin(@NotNull String login) {
      return new AuthData("", "");
    }

    @Override
    void onAuthSuccess() { }

    @Override
    void onAuthFailure() { }
  }

  private static final class ProviderAndData {
    private final @NotNull AuthDataProvider myProvider;
    private final @NotNull String myLogin;
    private final @NotNull String myPassword;

    private ProviderAndData(@NotNull AuthDataProvider provider, @NotNull String login, @NotNull String password) {
      myProvider = provider;
      myLogin = login;
      myPassword = password;
    }

    private @NotNull AuthDataProvider getProvider() {
      return myProvider;
    }

    private @NotNull String getLogin() {
      return myLogin;
    }

    private @NotNull String getPassword() {
      return myPassword;
    }

    @Override
    public @NonNls String toString() {
      return "provider='" + myProvider.getName() + "', login='" + myLogin + '\'';
    }
  }
}
