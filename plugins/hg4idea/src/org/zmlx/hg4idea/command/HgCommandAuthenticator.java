// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.ui.HgUsernamePasswordDialog;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.zmlx.hg4idea.command.HgErrorUtil.isAuthorizationError;

/**
 * Base class for any command interacting with a remote repository and which needs authentication.
 */
class HgCommandAuthenticator {

  private static final Logger LOG = Logger.getInstance(HgCommandAuthenticator.class.getName());

  @Nullable
  protected HgCommandResult executeCommandAndAuthenticateIfNecessary(Project project, VirtualFile localRepository, String remoteRepository, String command, List<String> arguments) {
    return executeCommandAndAuthenticateIfNecessary(project, localRepository, remoteRepository, command, arguments, 0);
  }
  /**
   * Tries to execute an hg command and analyzes the output.
   * If authentication is needed, watches to the PasswordSafe for the saved password. Otherwise asks the user.
   * Repeats it 3 times to give a chance to retry.
   * @param project
   * @param localRepository
   * @param remoteRepository
   * @param command
   * @param arguments
   * @param urlPosition
   * @return
   */
  @Nullable
  protected HgCommandResult executeCommandAndAuthenticateIfNecessary(Project project, VirtualFile localRepository, String remoteRepository, String command, List<String> arguments, int urlArgumentPosition) {
    HgCommandResult result = HgCommandService.getInstance(project).execute(localRepository, command, arguments); // try to execute without authentication data
    if (isAuthorizationError(result)) {
      try {
        // get auth data from password safe of from user and inject to the url
        HgUrl hgUrl = new HgUrl(remoteRepository);
        if (hgUrl.supportsAuthentication()) {
          final GetPasswordRunnable runnable = new GetPasswordRunnable(project, hgUrl);
          // first time try to get info from password safe if it's there, or show auth dialog if not
          result = tryToAuthenticate(project, localRepository, hgUrl, runnable, command, arguments, urlArgumentPosition);
          if (!isAuthorizationError(result)) {
            saveCredentials(project, runnable);
          } else {
            // then twice request auth info from user
            runnable.setForceShowDialog(true);
            for (int i = 0; i < 2; i++) {
              result = tryToAuthenticate(project, localRepository, hgUrl, runnable, command, arguments, urlArgumentPosition);
              if (!isAuthorizationError(result)) {
                saveCredentials(project, runnable);
                return result;
              }
            }
            HgUtil.notifyError(project, "Authentication failed", "Authentication to " + remoteRepository + " failed");
            return result;
          }
        } else {
          HgUtil.notifyError(project, "Authentication error", "Authentication was requested, but " + hgUrl.getScheme() + " doesn't support it.");
        }
      } catch (URISyntaxException e) {
        VcsUtil.showErrorMessage(project, "Invalid repository: " + remoteRepository, "Error");
      }
    }
    return result;
  }

  /**
   * Shows the auth dialog if needed, fills authentication data to the given hgurl and returns the result of command execution.
   * NB: hgUrl is modified
   */
  @Nullable
  private static HgCommandResult tryToAuthenticate(Project project, VirtualFile localRepository, HgUrl hgUrl, GetPasswordRunnable runnable, String command, List<String> arguments, int urlArgumentPosition) throws URISyntaxException {
    HgCommandService service = HgCommandService.getInstance(project);

    UIUtil.invokeAndWaitIfNeeded(runnable);
    if (runnable.isOk()) {
      hgUrl.setUsername(runnable.getUserName());
      hgUrl.setPassword(String.valueOf(runnable.getPassword()));
    }

    arguments.set(urlArgumentPosition, hgUrl.asString());
    return service.execute(localRepository, command, arguments);
  }

  private static void saveCredentials(Project project, GetPasswordRunnable runnable) {
    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    if (passwordSafe.getSettings().getProviderType().equals(PasswordSafeSettings.ProviderType.DO_NOT_STORE)) {
      return;
    }
    final String key = keyForUrlAndLogin(runnable.getURL(), runnable.getUserName());

    final PasswordSafeProvider provider = runnable.isRememberPassword() ? passwordSafe.getMasterKeyProvider() : passwordSafe.getMemoryProvider();
    try {
      provider.storePassword(project, HgCommandAuthenticator.class, key, runnable.getPassword());
      final HgVcs vcs = HgVcs.getInstance(project);
      if (vcs != null) {
        vcs.getGlobalSettings().addRememberedUrl(runnable.getURL(), runnable.getUserName());
      }
    } catch (PasswordSafeException e) {
      LOG.info("Couldn't store the password for key [" + key + "]", e);
    }
  }

  private static class GetPasswordRunnable implements Runnable {

    private final HgUrl hgUrl;
    private String userName;
    private String myPassword;
    private Project project;
    private boolean myForceShowDialog;
    private boolean ok = false;
    private static final Logger LOG = Logger.getInstance(GetPasswordRunnable.class.getName());
    private String myURL;
    private boolean myRememberPassword;

    public GetPasswordRunnable(Project project, HgUrl hgUrl) {
      this.hgUrl = hgUrl;
      this.project = project;
    }

    public void run() {

      // get the string representation of the url
      @Nullable String stringUrl = null;
      try {
        stringUrl = hgUrl.asString();
      }
      catch (URISyntaxException e) {
        LOG.warn("Couldn't parse hgUrl: [" + hgUrl + "]", e);
      }

      // find if we've already been here
      final HgVcs vcs = HgVcs.getInstance(project);
      if (vcs == null) { return; }

      final HgGlobalSettings hgGlobalSettings = vcs.getGlobalSettings();
      final Map<String, List<String>> urls = hgGlobalSettings.getRememberedUrls();
      @Nullable List<String> rememberedLoginsForUrl = urls.get(stringUrl);

      String login = hgUrl.getUsername();
      if (StringUtils.isBlank(login)) {
        // find the last used login
        if (rememberedLoginsForUrl != null && !rememberedLoginsForUrl.isEmpty()) {
          login = rememberedLoginsForUrl.get(0);
        }
      }

      String password = hgUrl.getPassword();
      if (StringUtils.isBlank(password) && stringUrl != null) {
        // if we've logged in with this login, search for password
        final String key = keyForUrlAndLogin(stringUrl, login);
        try {
          final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
          password = passwordSafe.getMemoryProvider().getPassword(project, HgCommandAuthenticator.class, key);
          if (password == null && passwordSafe.getSettings().getProviderType().equals(PasswordSafeSettings.ProviderType.MASTER_PASSWORD)) {
            password = passwordSafe.getMasterKeyProvider().getPassword(project, HgCommandAuthenticator.class, key);
          }
        } catch (PasswordSafeException e) {
          LOG.info("Couldn't get password for key [" + key + "]", e);
        }
      }

      // don't show dialog if we can (not forced + both fields are known)
      if (!myForceShowDialog && !StringUtils.isBlank(password) && !StringUtils.isBlank(login)) {
        userName = login;
        myPassword = password;
        ok = true;
        return;
      }

      final HgUsernamePasswordDialog dialog = new HgUsernamePasswordDialog(project, login, password);
      dialog.show();
      if (dialog.isOK()) {
        userName = dialog.getUsername();
        myPassword = dialog.getPassword();
        ok = true;

        myRememberPassword = dialog.isRememberPassword();
        if (stringUrl != null) {
          myURL = stringUrl;
        }
      }
    }

    public String getUserName() {
      return userName;
    }

    public String getPassword() {
      return myPassword;
    }

    public boolean isOk() {
      return ok;
    }

    public String getURL() {
      return myURL;
    }

    public void setForceShowDialog(boolean forceShowDialog) {
      myForceShowDialog = forceShowDialog;
    }

    public boolean isRememberPassword() {
      return myRememberPassword;
    }
  }

  private static String keyForUrlAndLogin(String stringUrl, String login) {
    return stringUrl + login;
  }

}
