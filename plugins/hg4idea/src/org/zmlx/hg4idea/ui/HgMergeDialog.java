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
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;

public class HgMergeDialog extends HgCommonDialogWithChoices {

  public HgMergeDialog(@NotNull Project project,
                       @NotNull Collection<HgRepository> repositories,
                       @Nullable HgRepository selectedRepo) {
    super(project, repositories, selectedRepo);
    hgRepositorySelectorComponent.setTitle(HgBundle.message("action.hg4idea.merge.select.repo"));
    myBranchesBorderPanel.setBorder(IdeBorderFactory.createTitledBorder(HgBundle.message("action.hg4idea.merge.with")));
    setTitle(HgBundle.message("action.hg4idea.Merge"));
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.merge.dialog";
  }
}
