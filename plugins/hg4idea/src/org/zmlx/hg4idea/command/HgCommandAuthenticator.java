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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.ui.HgUsernamePasswordDialog;

import java.net.URISyntaxException;
import java.util.List;

/**
 * Base class for any command interacting with a remote repository and which needs authentication.
 */
class HgCommandAuthenticator {

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

            arguments.set(arguments.size() - 1, hgUrl.asString());
            result = service.execute(localRepository, command, arguments);
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
    private char[] password;
    private Project project;
    private boolean ok = false;

    public GetPasswordRunnable(Project project, HgUrl hgUrl) {
      this.hgUrl = hgUrl;
      this.project = project;
    }

    public void run() {
      final HgUsernamePasswordDialog dialog = new HgUsernamePasswordDialog(project, hgUrl.getUsername());
      dialog.show();

      if (dialog.isOK()) {
        userName = dialog.getUsername();
        password = dialog.getPassword();
        ok = true;
      }
    }

    public String getUserName() {
      return userName;
    }

    public char[] getPassword() {
      return password.clone();
    }

    public boolean isOk() {
      return ok;
    }
  }

}
