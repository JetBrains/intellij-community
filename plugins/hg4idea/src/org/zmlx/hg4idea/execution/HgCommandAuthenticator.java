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
  private final Project myProject;
  private final boolean myForceAuthorization;
  //todo replace silent mode and/or force authorization
  private final boolean mySilentMode;

  public HgCommandAuthenticator(Project project, boolean forceAuthorization, boolean silent) {
    myProject = project;
    myForceAuthorization = forceAuthorization;
    mySilentMode = silent;
  }

  public void saveCredentials() {
    if (myGetPassword == null) return;

    final String url = VirtualFileManager.extractPath(myGetPassword.getURL());
    final String key = keyForUrlAndLogin(url, myGetPassword.getUserName());
    PasswordSafe.getInstance().setPassword(HgCommandAuthenticator.class, key, myGetPassword.getPassword(), !myGetPassword.isRememberPassword());
    final HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs != null) {
      vcs.getGlobalSettings().addRememberedUrl(url, myGetPassword.getUserName());
    }
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
    private String myUserName;
    private String myPassword;
    private final Project myProject;
    @NotNull private final String myProposedLogin;
    private boolean ok = false;
    @NotNull private final String myURL;
    private boolean myRememberPassword;
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

      String password = null;
      if (!StringUtil.isEmptyOrSpaces(login)) {
        // if we've logged in with this login, search for password
        password = PasswordSafe.getInstance().getPassword(HgCommandAuthenticator.class, keyForUrlAndLogin(url, login));
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

    @NotNull
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
