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
package org.zmlx.hg4idea.execution;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.vcsUtil.AuthDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;

/**
 * Base class for any command interacting with a remote repository and which needs authentication.
 */
class HgCommandAuthenticator {

  private static final Logger LOG = Logger.getInstance(HgCommandAuthenticator.class.getName());
  
  private GetPasswordRunnable myGetPassword;
  private final Project myProject;
  private boolean myForceAuthorization;
  //todo replace silent mode and/or force authorization
  private boolean mySilentMode;

  public HgCommandAuthenticator(Project project, boolean forceAuthorization, boolean silent) {
    myProject = project;
    myForceAuthorization = forceAuthorization;
    mySilentMode = silent;
  }

  public void saveCredentials() {
    if (myGetPassword == null) return;

    // if checkbox is selected, save on disk. Otherwise in memory. Don't read password safe settings.

    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    final String url = VirtualFileManager.extractPath(myGetPassword.getURL());
    final String key = keyForUrlAndLogin(url, myGetPassword.getUserName());
    try {
      if (myGetPassword.isRememberPassword()) {
        PasswordSafe.getInstance().storePassword(myProject, HgCommandAuthenticator.class, key, myGetPassword.getPassword());
      }
      else if (passwordSafe.getSettings().getProviderType() != PasswordSafeSettings.ProviderType.DO_NOT_STORE) {
        passwordSafe.getMemoryProvider().storePassword(myProject, HgCommandAuthenticator.class, key, myGetPassword.getPassword());
      }
      final HgVcs vcs = HgVcs.getInstance(myProject);
      if (vcs != null) {
        vcs.getGlobalSettings().addRememberedUrl(url, myGetPassword.getUserName());
      }
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't store the password for key [" + key + "]", e);
    }
  }

  public boolean promptForAuthentication(Project project, String proposedLogin, String uri, String path, @Nullable ModalityState state) {
    GetPasswordRunnable runnable = new GetPasswordRunnable(project, proposedLogin, uri, path, myForceAuthorization, mySilentMode);
    ApplicationManager.getApplication().invokeAndWait(runnable, state == null ? ModalityState.defaultModalityState() : state);
    myGetPassword = runnable;
    return runnable.isOk();
  }

  public String getUserName() {
    return myGetPassword.getUserName();
  }

  public String getPassword() {
    return myGetPassword.getPassword();
  }

  private static class GetPasswordRunnable implements Runnable {
    private static final Logger LOG = Logger.getInstance(GetPasswordRunnable.class.getName());

    private String myUserName;
    private String myPassword;
    private Project myProject;
    private final String myProposedLogin;
    private boolean ok = false;
    @Nullable private String myURL;
    private boolean myRememberPassword;
    private boolean myForceAuthorization;
    private final boolean mySilent;

    public GetPasswordRunnable(Project project,
                               String proposedLogin,
                               String uri,
                               String path,
                               boolean forceAuthorization, boolean silent) {
      this.myProject = project;
      this.myProposedLogin = proposedLogin;
      this.myURL = uri + path;
      this.myForceAuthorization = forceAuthorization;
      mySilent = silent;
    }

    public void run() {

      // find if we've already been here
      final HgVcs vcs = HgVcs.getInstance(myProject);
      if (vcs == null) {
        return;
      }

      @NotNull final HgGlobalSettings hgGlobalSettings = vcs.getGlobalSettings();
      @Nullable String rememberedLoginsForUrl = null;
      if (!StringUtil.isEmptyOrSpaces(myURL)) {
        rememberedLoginsForUrl = hgGlobalSettings.getRememberedUserName(VirtualFileManager.extractPath(myURL));
      }

      String login = myProposedLogin;
      if (StringUtil.isEmptyOrSpaces(login)) {
        // find the last used login
        login = rememberedLoginsForUrl;
      }

      String password = null;
      if (!StringUtil.isEmptyOrSpaces(login) && myURL != null) {
        // if we've logged in with this login, search for password
        final String key = keyForUrlAndLogin(myURL, login);
        try {
          final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
          if (mySilent) {
            password = passwordSafe.getMemoryProvider().getPassword(myProject, HgCommandAuthenticator.class, key);
          }
          else {
            password = passwordSafe.getPassword(myProject, HgCommandAuthenticator.class, key);
          }
        }
        catch (PasswordSafeException e) {
          LOG.info("Couldn't get password for key [" + key + "]", e);
        }
      }

      // don't show dialog if we don't have to (both fields are known) except force authorization required
      if (!myForceAuthorization && !StringUtil.isEmptyOrSpaces(
        password) && !StringUtil.isEmptyOrSpaces(login)) {
        myUserName = login;
        myPassword = password;
        ok = true;
        return;
      }

      if (mySilent) {
        ok = false;
        return;
      }

      final AuthDialog dialog = new AuthDialog(myProject, HgVcsMessages.message("hg4idea.dialog.login.password.required"),
                                               HgVcsMessages.message("hg4idea.dialog.login.description", myURL),
                                               login, password, true);
      if (dialog.showAndGet()) {
        myUserName = dialog.getUsername();
        myPassword = dialog.getPassword();
        myRememberPassword = dialog.isRememberPassword();
        ok = true;
      }
    }

    public String getUserName() {
      return myUserName;
    }

    public String getPassword() {
      return myPassword;
    }

    public boolean isOk() {
      return ok;
    }

    @Nullable
    public String getURL() {
      return myURL;
    }

    public boolean isRememberPassword() {
      return myRememberPassword;
    }
  }

  private static String keyForUrlAndLogin(String stringUrl, String login) {
    return login + ":" + stringUrl;
  }
}
