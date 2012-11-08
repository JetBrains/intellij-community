/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Git "revert" action
 */
public class GitRevert extends BasicAction {

  @Override
  public boolean perform(@NotNull final Project project, GitVcs vcs, @NotNull final List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification("Can not revert now")) return true;
    final List<Change> changes = new ArrayList<Change>(affectedFiles.length);
    for (VirtualFile f : affectedFiles) {
      Change ch = changeListManager.getChange(f);
      if (ch != null) {
        changes.add(ch);
      }
    }
    RollbackChangesDialog.rollbackChanges(project, changes);
    for (GitRepository repository : GitUtil.getRepositoriesForFiles(project, Arrays.asList(affectedFiles))) {
      repository.update();
    }
    return false;
  }

  @Override
  @NotNull
  protected String getActionName() {
    return UIUtil.removeMnemonic(GitBundle.getString("revert.action.name"));
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    for (VirtualFile file : vFiles) {
      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
      if (file.isDirectory() || (fileStatus != FileStatus.NOT_CHANGED && fileStatus != FileStatus.UNKNOWN)) {
        return true;
      }
    }
    return false;
  }
}
