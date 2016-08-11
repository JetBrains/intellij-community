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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgRevertCommand;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
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
    List<FilePath> toDelete = new ArrayList<>();
    List<FilePath> filePaths = new LinkedList<>();
    for (Change change : changes) {
      ContentRevision contentRevision;
      if (Change.Type.DELETED == change.getType()) {
        contentRevision = change.getBeforeRevision();
      }
      else {
        contentRevision = change.getAfterRevision();
      }
      if (contentRevision != null) {
        filePaths.add(contentRevision.getFile());
        if (Change.Type.MOVED == change.getType()) {
          toDelete.add(contentRevision.getFile());
        }
      }
    }
    AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
    try {
      revert(filePaths);
      for (FilePath file : toDelete) {
        listener.accept(file);
        try {
          final File ioFile = file.getIOFile();
          if (ioFile.exists()) {
            if (!ioFile.delete()) {
              //noinspection ThrowableInstanceNeverThrown
              vcsExceptions.add(new VcsException("Unable to delete file: " + file));
            }
          }
        }
        catch (Exception e) {
          //noinspection ThrowableInstanceNeverThrown
          vcsExceptions.add(new VcsException("Unable to delete file: " + file, e));
        }
      }
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(project, token);
    }
  }

  public void rollbackMissingFileDeletion(List<FilePath> files,
                                          List<VcsException> exceptions, RollbackProgressListener listener) {
    AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
    try {
      revert(files);
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(project, token);
    }
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

  private void revert(@NotNull List<FilePath> filePaths) {
    for (Map.Entry<VirtualFile, Collection<FilePath>> entry : HgUtil.groupFilePathsByHgRoots(project, filePaths).entrySet()) {
      final VirtualFile repo = entry.getKey();
      final Collection<FilePath> files = entry.getValue();

      HgRevisionNumber revisionNumber = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
      for (List<String> chunk : VcsFileUtil.chunkPaths(repo, files)) {
        HgCommandResult revertResult = new HgRevertCommand(project).execute(repo, chunk, revisionNumber, false);
        if (HgErrorUtil.hasUncommittedChangesConflict(revertResult)) {

          String message = String.format("<html>Revert failed due to uncommitted merge.<br>" +
                                         "Would you like to discard all changes for repository <it><b>%s</b></it>?</html>",
                                         repo.getPresentableName());

          int exitCode = HgUpdateCommand.showDiscardChangesConfirmation(project, message);
          if (exitCode == Messages.OK) {
            //discard all changes for this repository//
            HgUpdateCommand updateCommand = new HgUpdateCommand(project, repo);
            updateCommand.setClean(true);
            updateCommand.setRevision(".");
            updateCommand.execute();
          }
          break;
        }
        new HgResolveCommand(project).markResolved(repo, files);
      }
    }
  }

}
