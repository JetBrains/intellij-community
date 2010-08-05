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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.rollback.GitRollbackEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Git "revert" action
 */
public class GitRevert extends BasicAction {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean perform(@NotNull final Project project,
                         GitVcs vcs,
                         @NotNull final List<VcsException> exceptions,
                         @NotNull VirtualFile[] affectedFiles) {
    saveAll();
    final ChangeListManager changeManager = ChangeListManager.getInstance(project);
    final List<Change> changes = new ArrayList<Change>();
    final HashSet<VirtualFile> files = new HashSet<VirtualFile>();
    try {
      for (VirtualFile f : affectedFiles) {
        Change ch = changeManager.getChange(f);
        if (ch != null) {
          files.add(GitUtil.getGitRoot(f));
          changes.add(ch);
        }
      }
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return true;
    }
    return toBackground(project, vcs, affectedFiles, exceptions, new Consumer<ProgressIndicator>() {
      public void consume(ProgressIndicator pi) {
        pi.setIndeterminate(true);
        GitRollbackEnvironment re = GitRollbackEnvironment.getInstance(project);
        re.rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY);
        if (changes.size() == 1) {
          Change c = changes.get(0);
          ContentRevision r = c.getAfterRevision();
          if (r == null) {
            r = c.getBeforeRevision();
            assert r != null;
          }
          pi.setText2(r.getFile().getPath());
        }
        else {
          pi.setText2(GitBundle.message("revert.reverting.mulitple", changes.size()));
        }
        final VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
        for (Change c : changes) {
          ContentRevision before = c.getBeforeRevision();
          if (before != null) {
            mgr.fileDirty(before.getFile());
          }
          ContentRevision after = c.getAfterRevision();
          if (after != null) {
            mgr.fileDirty(after.getFile());
          }
        }
      }
    });
  }

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("revert.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    for (VirtualFile file : vFiles) {
      FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
      if (status == FileStatus.UNKNOWN || status == FileStatus.NOT_CHANGED) return false;
    }
    return true;
  }
}
