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

import com.intellij.execution.util.ExecutableValidator;
import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.command.HgVersionCommand;

public class HgExecutableValidator extends ExecutableValidator {

  private final HgVcs myVcs;

  public HgExecutableValidator(Project project) {
    super(project, HgVcs.NOTIFICATION_GROUP_ID, HgVcs.getInstance(project).getConfigurable());
    myVcs = HgVcs.getInstance(project);
    setMessagesAndTitles(HgVcsMessages.message("hg4idea.executable.notification.title"),
                         HgVcsMessages.message("hg4idea.executable.notification.description"),
                         HgVcsMessages.message("hg4idea.executable.dialog.title"),
                         HgVcsMessages.message("hg4idea.executable.dialog.description"),
                         HgVcsMessages.message("hg4idea.executable.dialog.error"),
                         HgVcsMessages.message("hg4idea.executable.filechooser.title"),
                         HgVcsMessages.message("hg4idea.executable.filechooser.description"));
  }

  @Override
  protected String getCurrentExecutable() {
    return myVcs.getHgExecutable();
  }

  @Override
  public boolean isExecutableValid(String executable) {
    return new HgVersionCommand().isValid(executable);
  }

  @Override
  protected void saveCurrentExecutable(String executable) {
    myVcs.getGlobalSettings().setHgExecutable(executable);
  }

}
