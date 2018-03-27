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

import com.intellij.execution.ExecutableValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.util.HgUtil;

public class HgExecutableValidator extends ExecutableValidator {
  public HgExecutableValidator(@NotNull Project project) {
    super(project,
          HgVcsMessages.message("hg4idea.executable.notification.title"),
          HgVcsMessages.message("hg4idea.executable.notification.description"));
  }

  @Override
  protected String getCurrentExecutable() {
    return HgExecutableManager.getInstance().getHgExecutable(myProject);
  }

  @NotNull
  @Override
  protected String getConfigurableDisplayName() {
    return HgProjectConfigurable.DISPLAY_NAME;
  }

  @Override
  public boolean isExecutableValid(@NotNull String executable) {
    return HgUtil.isExecutableValid(executable);
  }
}
