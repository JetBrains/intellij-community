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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgUpdateToDialog;

import java.util.Collection;

public class HgUpdateToAction extends HgAbstractGlobalSingleRepoAction {

  @Override
  protected void execute(@NotNull final Project project,
                         @NotNull final Collection<HgRepository> repositories,
                         @Nullable HgRepository selectedRepo) {
    final HgUpdateToDialog dialog = new HgUpdateToDialog(project, repositories, selectedRepo);
    if (dialog.showAndGet()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final String updateToValue = StringUtil.escapeBackSlashes(dialog.getTargetValue());
      final boolean clean = dialog.isRemoveLocalChanges();
      final VirtualFile root = dialog.getRepository().getRoot();
      HgUpdateCommand.updateRepoTo(project, root, updateToValue, clean, null);
    }
  }
}
