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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;

public class HgUpdateToDialog extends HgCommonDialogWithChoices {

  public HgUpdateToDialog(Project project, @NotNull Collection<HgRepository> repos, @Nullable HgRepository selectedRepo) {
    super(project, repos, selectedRepo);
    myBranchesBorderPanel.setBorder(IdeBorderFactory.createTitledBorder("Switch to", true));
    hgRepositorySelectorComponent.setTitle("Select repository to switch");
    setTitle("Switch Working Directory");
    cleanCbx.setVisible(true);
    cleanCbx.setEnabled(true);
  }

  public boolean isRemoveLocalChanges() {
    return cleanCbx.isSelected();
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.switch.working.directory";
  }
}
