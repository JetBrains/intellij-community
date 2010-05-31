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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.*;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;

import java.util.*;

public class HgRollbackEnvironment implements RollbackEnvironment {

  private final Project project;

  public HgRollbackEnvironment(Project project) {
    this.project = project;
  }

  public String getRollbackOperationName() {
    return HgVcsMessages.message("hg4idea.revert");
  }

  public void rollbackChanges(List<Change> changes, List<VcsException> vcsExceptions,
    @NotNull RollbackProgressListener listener) {
    if (changes == null || changes.isEmpty()) {
      return;
    }
    List<FilePath> filePaths = new LinkedList<FilePath>();
    for (Change change : changes) {
      ContentRevision contentRevision;
      if (Change.Type.DELETED == change.getType()) {
        contentRevision = change.getBeforeRevision();
      } else {
        contentRevision = change.getAfterRevision();
      }
      if (contentRevision != null) {
        filePaths.add(contentRevision.getFile());
      }
    }
    revert(filePaths);
  }

  public void rollbackMissingFileDeletion(List<FilePath> files,
    List<VcsException> exceptions, RollbackProgressListener listener) {
    revert(files);
  }

  public void rollbackModifiedWithoutCheckout(List<VirtualFile> files,
    List<VcsException> exceptions, RollbackProgressListener listener) {
  }

  public List<VcsException> rollbackMissingFileDeletion(List<FilePath> files) {
    if (files == null || files.isEmpty()) {
      return null;
    }
    revert(files);
    return null;
  }

  public void rollbackIfUnchanged(VirtualFile file) {
  }

  private void revert(List<FilePath> filePaths) {
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);

    HgWorkingCopyRevisionsCommand identifyCommand = new HgWorkingCopyRevisionsCommand(project);
    HgRevertCommand revertCommand = new HgRevertCommand(project);
    HgResolveCommand resolveCommand = new HgResolveCommand(project);

    for (FilePath filePath : filePaths) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
      if (vcsRoot == null) {
        continue;
      }

      HgFile hgFile = new HgFile(vcsRoot, filePath);

      HgRevisionNumber revisionNumber = identifyCommand.firstParent(vcsRoot);
      revertCommand.execute(hgFile, revisionNumber, false);
      resolveCommand.markResolved(vcsRoot, filePath);

      dirtyScopeManager.dirDirtyRecursively(filePath.getParentPath());
    }
  }

}
