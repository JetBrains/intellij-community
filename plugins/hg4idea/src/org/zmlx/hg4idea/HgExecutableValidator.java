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
package org.zmlx.hg4idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import org.zmlx.hg4idea.command.HgVersionCommand;
import org.zmlx.hg4idea.ui.HgSetExecutableDialog;

import java.lang.reflect.InvocationTargetException;

public class HgExecutableValidator {

  private static final Logger LOG = Logger.getInstance(HgExecutableValidator.class.getName());
  private final Project myProject;
  private boolean myValidHgExecutable;

  public HgExecutableValidator(Project project) {
    this.myProject = project;
  }

  public boolean check(final HgGlobalSettings globalSettings) {
    final HgVersionCommand command = new HgVersionCommand();
    if (command.isValid(HgVcs.getInstance(myProject).getHgExecutable())) {
      return true;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }
    myValidHgExecutable = false;

    try {
      GuiUtils.runOrInvokeAndWait(new Runnable() {
        public void run() {
          String previousHgPath = globalSettings.getHgExecutable();
          HgSetExecutableDialog dialog;
          do {
            dialog = new HgSetExecutableDialog(myProject);
            dialog.setBadHgPath(previousHgPath);
            dialog.show();
            myValidHgExecutable = dialog.isOK() && command.isValid(dialog.getNewHgPath());
            previousHgPath = dialog.getNewHgPath();
          } while (!myValidHgExecutable && dialog.isOK());
          if (myValidHgExecutable) {
            globalSettings.setHgExecutable(dialog.getNewHgPath());
          }
        }
      });
    } catch (InvocationTargetException e) {
      LOG.error(e);
    } catch (InterruptedException e) {
      LOG.error(e);
    }
    return myValidHgExecutable;
  }

}
