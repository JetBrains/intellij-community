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

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
  private GetPasswordRunnable myGetPassword;
  private final boolean myForceAuthorization;
  //todo replace silent mode and/or force authorization
  private final boolean mySilentMode;

  public HgCommandAuthenticator(boolean forceAuthorization, boolean silent) {
    myForceAuthorization = forceAuthorization;
    mySilentMode = silent;
  }

  public void forgetPassword() {
    if (myGetPassword == null) return;    // prompt was not suggested;
    String url = VirtualFileManager.extractPath(myGetPassword.getURL());
    PasswordSafe.getInstance().set(createCredentialAttributes(url), null);
  }

  public boolean promptForAuthentication(Project project, @NotNull String proposedLogin, @NotNull String uri, @NotNull String path, @Nullable ModalityState state) {
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
    private Credentials myCredentials;
    private final Project myProject;
    @NotNull private final String myProposedLogin;
    private boolean ok = false;
    @NotNull private final String myURL;
    private final boolean myForceAuthorization;
    private final boolean mySilent;

    public GetPasswordRunnable(Project project,
                               @NotNull String proposedLogin,
                               @NotNull String uri,
                               @NotNull String path,
                               boolean forceAuthorization, boolean silent) {
      myProject = project;
      myProposedLogin = proposedLogin;
      myURL = uri + path;
      myForceAuthorization = forceAuthorization;
      mySilent = silent;
    }

    @Override
    public void run() {

      // find if we've already been here
      final HgVcs vcs = HgVcs.getInstance(myProject);
      if (vcs == null) {
        return;
      }

      @NotNull final HgGlobalSettings hgGlobalSettings = vcs.getGlobalSettings();
      @Nullable String rememberedLoginsForUrl = null;
      String url = VirtualFileManager.extractPath(myURL);
      if (!StringUtil.isEmptyOrSpaces(myURL)) {
        rememberedLoginsForUrl = hgGlobalSettings.getRememberedUserName(url);
      }

      String login = myProposedLogin;
      if (StringUtil.isEmptyOrSpaces(login)) {
        // find the last used login
        login = rememberedLoginsForUrl;
      }

      Credentials savedCredentials = PasswordSafe.getInstance().get(createCredentialAttributes(url));
      String password = savedCredentials == null ? null : savedCredentials.getPasswordAsString();
      if (savedCredentials != null && StringUtil.isEmptyOrSpaces(login)) {
        login = savedCredentials.getUserName();
      }

      // don't show dialog if we don't have to (both fields are known) except force authorization required
      if (!myForceAuthorization && !StringUtil.isEmptyOrSpaces(password) && !StringUtil.isEmptyOrSpaces(login)) {
        myCredentials = new Credentials(login, password);
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
        ok = true;
        Credentials credentials = new Credentials(dialog.getUsername(), dialog.getPassword());
        myCredentials = credentials;
        PasswordSafe.getInstance().set(createCredentialAttributes(url), credentials, !dialog.isRememberPassword());
        hgGlobalSettings.addRememberedUrl(url, credentials.getUserName());
      }
    }

    public String getUserName() {
      return myCredentials.getUserName();
    }

    public String getPassword() {
      return myCredentials == null ? null : myCredentials.getPasswordAsString();
    }

    public boolean isOk() {
      return ok;
    }

    @NotNull
    public String getURL() {
      return myURL;
    }
  }

  @NotNull
  private static CredentialAttributes createCredentialAttributes(@NotNull String url) {
    return new CredentialAttributes(CredentialAttributesKt.generateServiceName("HG", url), null);
  }
}
