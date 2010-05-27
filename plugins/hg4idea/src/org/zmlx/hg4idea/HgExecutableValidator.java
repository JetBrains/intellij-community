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

import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.ui.HgSetExecutableDialog;

import java.io.File;

/**
 * Validates the 'hg' executable file, which is specified in {@link HgGlobalSettings}}.
 * Checks that the file specified exists and invokes the {@link org.zmlx.hg4idea.ui.HgSetExecutableDialog}
 * if not. The dialog provides more detailed validation.
 */
class HgExecutableValidator {

  private final Project myProject;

  public HgExecutableValidator(Project project) {
    this.myProject = project;
  }

  public boolean check(HgGlobalSettings globalSettings) {
    String hgPath = globalSettings.getHgExecutable();
    if ((new File(hgPath)).exists()) {
      return true;
    }

    boolean validHgExecutable;
    HgSetExecutableDialog dialog;
    do {
      dialog = new HgSetExecutableDialog(myProject);
      dialog.setBadHgPath(hgPath);
      dialog.show();
      validHgExecutable = dialog.isOK();
      hgPath = dialog.getNewHgPath();
    } while (!validHgExecutable && dialog.isOK());

    if (validHgExecutable) {
      globalSettings.setHgExecutable(dialog.getNewHgPath());
      return true;
    }

    return false;
  }

}
