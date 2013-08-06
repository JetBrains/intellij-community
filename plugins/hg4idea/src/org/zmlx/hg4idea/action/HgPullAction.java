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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgPullCommand;
import org.zmlx.hg4idea.ui.HgPullDialog;

import java.util.Collection;

public class HgPullAction extends HgAbstractGlobalAction {
  public HgPullAction() {
    super(AllIcons.Actions.CheckOut);
  }

  @Override
  protected void execute(@NotNull final Project project, @NotNull Collection<VirtualFile> repos, @Nullable VirtualFile selectedRepo) {
    final HgPullDialog dialog = new HgPullDialog(project);
    dialog.setRoots(repos, selectedRepo);
    dialog.show();
    if (dialog.isOK()) {
      dialog.rememberSettings();
      new Task.Backgroundable(project, "Pulling changes from " + dialog.getSource(), false) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          executePull(dialog, project);
          markDirtyAndHandleErrors(project, dialog.getRepository());
        }
      }.queue();
    }
  }

  private static void executePull(final HgPullDialog dialog, final Project project) {
    final HgPullCommand command = new HgPullCommand(
      project, dialog.getRepository()
    );
    command.setSource(dialog.getSource());
    command.setRebase(false);
    command.setUpdate(false);

    command.execute();
  }
}
