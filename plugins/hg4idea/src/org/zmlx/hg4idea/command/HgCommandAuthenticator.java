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
import com.intellij.idea.LoggerFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.ui.HgUsernamePasswordDialog;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Base class for any command interacting with a remote repository and which needs authentication.
 */
class HgCommandAuthenticator {

  private static final Logger LOG = Logger.getInstance(HgCommandAuthenticator.class.getName());

  @Nullable
  protected HgCommandResult executeCommandAndAuthenticateIfNecessary(Project project, VirtualFile localRepository, String remoteRepository, String command, List<String> arguments) {
    HgCommandService service = HgCommandService.getInstance(project);
    HgCommandResult result = service.execute(localRepository, command, arguments);
    if (HgErrorUtil.isAbort(result) && HgErrorUtil.isAuthorizationRequiredAbort(result)) {
      try {
        HgUrl hgUrl = new HgUrl(remoteRepository);
        if (hgUrl.supportsAuthentication()) {
          GetPasswordRunnable runnable = new GetPasswordRunnable(project, hgUrl);
          if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.defaultModalityState());
          } else {
            runnable.run();
          }

          if ( runnable.isOk() ) {
            hgUrl.setUsername( runnable.getUserName() );
            hgUrl.setPassword(String.valueOf( runnable.getPassword() ));

            arguments.set(0, hgUrl.asString());
            result = service.execute(localRepository, command, arguments);

            if (result != null && result.getExitValue() == 0) {
              final String key = keyForUrlAndLogin(runnable.getURL(), runnable.getUserName());
              try {
                PasswordSafe.getInstance().storePassword(project, HgCommandAuthenticator.class, key, runnable.getPassword());
                HgVcs.getInstance(project).getGlobalSettings().addRememberedUrl(runnable.getURL(), runnable.getUserName());
              }
              catch (PasswordSafeException e) {
                LOG.error("Couldn't store the password for key [" + key + "]", e);
              }
            }

          }
        }
      } catch (URISyntaxException e) {
        VcsUtil.showErrorMessage(project, "Invalid repository: " + remoteRepository, "Error");
      }
    }
    return result;
  }

  private static class GetPasswordRunnable implements Runnable {

    private final HgUrl hgUrl;
    private String userName;
    private String myPassword;
    private Project project;
    private boolean ok = false;
    private static final Logger LOG = Logger.getInstance(GetPasswordRunnable.class.getName());
    private String myURL;

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
      final HgGlobalSettings hgGlobalSettings = HgVcs.getInstance(project).getGlobalSettings();
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
          password = PasswordSafe.getInstance().getPassword(project, HgCommandAuthenticator.class, key);
        } catch (PasswordSafeException e) {
          LOG.error("Couldn't get password for key [" + key + "]", e);
        }
      }
      
      final HgUsernamePasswordDialog dialog = new HgUsernamePasswordDialog(project, login, password);
      dialog.show();
      if (dialog.isOK()) {
        userName = dialog.getUsername();
        myPassword = dialog.getPassword();
        ok = true;

        if (dialog.isRememberPassword() && stringUrl != null) {
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
  }

  private static String keyForUrlAndLogin(String stringUrl, String login) {
    return stringUrl + login;
  }

}
