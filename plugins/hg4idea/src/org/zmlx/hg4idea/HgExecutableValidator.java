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
import org.zmlx.hg4idea.command.HgVersionCommand;
import org.zmlx.hg4idea.ui.HgSetExecutableDialog;

class HgExecutableValidator {

  private final Project project;

  public HgExecutableValidator(Project project) {
    this.project = project;
  }

  public boolean check(HgGlobalSettings globalSettings) {
    HgVersionCommand command = new HgVersionCommand();
    if (command.isValid(globalSettings.getHgExecutable())) {
      return true;
    }

    String previousHgPath = globalSettings.getHgExecutable();
    boolean validHgExecutable;
    HgSetExecutableDialog dialog;
    do {
      dialog = new HgSetExecutableDialog(project);
      dialog.setBadHgPath(previousHgPath);
      dialog.show();
      validHgExecutable = dialog.isOK() && command.isValid(dialog.getNewHgPath());
      previousHgPath = dialog.getNewHgPath();
    } while (!validHgExecutable && dialog.isOK());

    if (validHgExecutable) {
      globalSettings.setHgExecutable(dialog.getNewHgPath());
      return true;
    }

    return false;
  }

}
