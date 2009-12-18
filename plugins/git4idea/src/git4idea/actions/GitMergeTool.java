/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Git merge tool for resolving conflicts. Use IDEA built-in 3-way merge tool.
 */
public class GitMergeTool extends BasicAction {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean perform(@NotNull Project project,
                         GitVcs vcs,
                         @NotNull List<VcsException> exceptions,
                         @NotNull VirtualFile[] affectedFiles) {
    saveAll();
    // ensure that all selected files actually has unresolved conflicts
    ChangeListManager changes = ChangeListManager.getInstance(project);
    for (VirtualFile file : affectedFiles) {
      Change change = changes.getChange(file);
      if (change != null && change.getFileStatus() != FileStatus.MERGED_WITH_CONFLICTS) {
        File f = new File(file.getPath());
        //noinspection ThrowableInstanceNeverThrown
        exceptions.add(new VcsException(GitBundle.message("merge.is.not.needed", f.getAbsolutePath())));
        return true;
      }
    }
    // perform merge
    AbstractVcsHelper.getInstance(project).showMergeDialog(Arrays.asList(affectedFiles), vcs.getMergeProvider());
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("merge.tool.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    FileStatusManager fs = FileStatusManager.getInstance(project);
    for (VirtualFile f : vFiles) {
      if (fs.getStatus(f) != FileStatus.MERGED_WITH_CONFLICTS) {
        return false;
      }
    }
    return true;
  }
}
