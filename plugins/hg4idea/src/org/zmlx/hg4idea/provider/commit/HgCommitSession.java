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
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.*;
import org.apache.commons.lang.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;

import javax.swing.*;
import java.util.*;

public class HgCommitSession implements CommitSession {

  private final Project project;

  public HgCommitSession(Project project) {
    this.project = project;
  }

  @Deprecated
  public JComponent getAdditionalConfigurationUI() {
    return null;
  }

  public JComponent getAdditionalConfigurationUI(Collection<Change> changes, String commitMessage) {
    return null;
  }

  public boolean canExecute(Collection<Change> changes, String commitMessage) {
    return changes != null && !changes.isEmpty() && StringUtils.isNotBlank(commitMessage);
  }

  public void execute(Collection<Change> changes, String commitMessage) {
    for (VirtualFile root : extractRoots(changes)) {
      HgCommitCommand command = new HgCommitCommand(project, root, commitMessage);
      try {
        command.execute();
        VcsUtil.showStatusMessage(
          project, HgVcsMessages.message("hg4idea.commit.success", root.getPath())
        );
        HgUtil.markDirectoryDirty(project, root);
        root.refresh(true, true);
      } catch (HgCommandException e) {
        VcsUtil.showErrorMessage(project, e.getMessage(), "Error");
      } catch (VcsException e) {
        VcsUtil.showErrorMessage(project, e.getMessage(), "Error");
      }
    }
  }

  public void executionCanceled() {
  }

  private Set<VirtualFile> extractRoots(Collection<Change> changes) {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();

      FilePath filePath = null;
      if (afterRevision != null) {
        filePath = afterRevision.getFile();
      } else if (beforeRevision != null) {
        filePath = beforeRevision.getFile();
      }

      if (filePath == null || filePath.isDirectory()) {
        continue;
      }

      VirtualFile repo = VcsUtil.getVcsRootFor(project, filePath);
      if (repo != null) {
        result.add(repo);
      }
    }
    return result;
  }

}
