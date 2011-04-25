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
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.ui.HgAuthDialog;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;

/**
 * Base class for any command interacting with a remote repository and which needs authentication.
 */
class HgCommandAuthenticator {

  private static final Logger LOG = Logger.getInstance(HgCommandAuthenticator.class.getName());
  
  private GetPasswordRunnable myRunnable;
  private final Project myProject;

  public HgCommandAuthenticator(Project project) {
    myProject = project;
  }

  public void saveCredentials() {
    if (myRunnable == null) return;

    // if checkbox is selected, remember on disk. Otherwise in memory. Don't read password safe settings.

    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    final String url = stripSchemaFromUrl(myRunnable.getURL());
    final String key = keyForUrlAndLogin(url, myRunnable.getUserName());

    final PasswordSafeProvider provider =
      myRunnable.isRememberPassword() ? passwordSafe.getMasterKeyProvider() : passwordSafe.getMemoryProvider();
    try {
      provider.storePassword(myProject, HgCommandAuthenticator.class, key, myRunnable.getPassword());
      final HgVcs vcs = HgVcs.getInstance(myProject);
      if (vcs != null) {
        vcs.getGlobalSettings().addRememberedUrl(url, myRunnable.getUserName());
      }
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't store the password for key [" + key + "]", e);
    }
  }

  /**
   * Removes schema (protocol) part from url. For example, makes "bitbucket.org" from "http://bitbucket.org".
   */
  private static String stripSchemaFromUrl(String url) {
    final String schemaSeparator = "://";
    int ind = url.indexOf(schemaSeparator);
    if (ind != -1) {
      return url.substring(ind + schemaSeparator.length());
    }
    return url;
  }

  public boolean promptForAuthentication(Project project, String proposedLogin, String uri, String path) {
    GetPasswordRunnable runnable = new GetPasswordRunnable(project, proposedLogin, uri, path);
    // Don't use Application#invokeAndWait here, as IntelliJ 
    // may already be showing a dialog (such as the clone dialog)
    try {
      EventQueue.invokeAndWait(runnable); // TODO: use ApplicationManager.getApplication() with correct modality state.
      myRunnable = runnable;
      return runnable.isOk();
    }
    catch (InterruptedException e) {
      return false;
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public String getUserName() {
    return myRunnable.getUserName();
  }

  public String getPassword() {
    return myRunnable.getPassword();
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

    public GetPasswordRunnable(Project project, String proposedLogin, String uri, String path) {
      this.myProject = project;
      this.myProposedLogin = proposedLogin;
      this.myURL = uri + path;
    }
    
    public void run() {

      // find if we've already been here
      final HgVcs vcs = HgVcs.getInstance(myProject);
      if (vcs == null) { return; }

      final HgGlobalSettings hgGlobalSettings = vcs.getGlobalSettings();
      @Nullable String rememberedLoginsForUrl = null;
      if (!StringUtils.isBlank(myURL)) {
        rememberedLoginsForUrl = hgGlobalSettings.getRememberedUserName(stripSchemaFromUrl(myURL));
      }

      String login = myProposedLogin;
      if (StringUtils.isBlank(login)) {
        // find the last used login
        login = rememberedLoginsForUrl;
      }

      String password = null;
      if (!StringUtils.isBlank(login) && myURL != null) {
        // if we've logged in with this login, search for password
        final String key = keyForUrlAndLogin(myURL, login);
        try {
          final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
          password = passwordSafe.getMemoryProvider().getPassword(myProject, HgCommandAuthenticator.class, key);
          if (password == null) {
            final MasterKeyPasswordSafe masterKeyProvider = passwordSafe.getMasterKeyProvider();
            if (!masterKeyProvider.isEmpty()) {
              // workaround for: don't ask for master password, if the requested password is not there.
              // this should be fixed in PasswordSafe: don't ask master password to look for keys
              // until then we assume that is PasswordSafe was used (there is anything there), then it makes sense to look there.
              password = masterKeyProvider.getPassword(myProject, HgCommandAuthenticator.class, key);
            }
          }
        } catch (PasswordSafeException e) {
          LOG.info("Couldn't get password for key [" + key + "]", e);
        }
      }

      // don't show dialog if we don't have to (both fields are known)
      if (!StringUtils.isBlank(password) && !StringUtils.isBlank(login)) {
        myUserName = login;
        myPassword = password;
        ok = true;
        return;
      }

      final HgAuthDialog dialog = new HgAuthDialog(myProject, myURL, login, password);
      dialog.show();
      if (dialog.isOK()) {
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
