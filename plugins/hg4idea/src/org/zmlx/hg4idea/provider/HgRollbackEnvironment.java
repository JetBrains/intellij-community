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
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgRevisionNumber;
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

  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public String getRollbackOperationName() {
    return HgBundle.message("hg4idea.revert");
  }

  @Override
  public void rollbackChanges(List<? extends Change> changes, List<VcsException> vcsExceptions,
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
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, getRollbackOperationName())) {
      revert(filePaths);
      for (FilePath file : toDelete) {
        listener.accept(file);
        try {
          final File ioFile = file.getIOFile();
          if (ioFile.exists()) {
            if (!ioFile.delete()) {
              vcsExceptions.add(new VcsException(HgBundle.message("error.cannot.delete.file", file.getPresentableUrl())));
            }
          }
        }
        catch (Exception e) {
          vcsExceptions.add(new VcsException(HgBundle.message("error.cannot.delete.file", file.getPresentableUrl()), e));
        }
      }
    }
  }

  @Override
  public void rollbackMissingFileDeletion(List<? extends FilePath> files,
                                          List<? super VcsException> exceptions, RollbackProgressListener listener) {
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, getRollbackOperationName())) {
      revert(files);
    }
  }

  @Override
  public void rollbackModifiedWithoutCheckout(List<? extends VirtualFile> files,
                                              List<? super VcsException> exceptions, RollbackProgressListener listener) {
  }

  public List<VcsException> rollbackMissingFileDeletion(List<? extends FilePath> files) {
    if (files == null || files.isEmpty()) {
      return null;
    }
    revert(files);
    return null;
  }

  @Override
  public void rollbackIfUnchanged(VirtualFile file) {
  }

  private void revert(@NotNull List<? extends FilePath> filePaths) {
    for (Map.Entry<VirtualFile, Collection<FilePath>> entry : HgUtil.groupFilePathsByHgRoots(project, filePaths).entrySet()) {
      final VirtualFile repo = entry.getKey();
      final Collection<FilePath> files = entry.getValue();

      HgRevisionNumber revisionNumber = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
      for (List<String> chunk : VcsFileUtil.chunkPaths(repo, files)) {
        HgCommandResult revertResult = new HgRevertCommand(project).execute(repo, chunk, revisionNumber, false);
        if (HgErrorUtil.hasUncommittedChangesConflict(revertResult)) {

          String message = XmlStringUtil.wrapInHtml(HgBundle.message("hg4idea.revert.failed.msg", repo.getPresentableName()));
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
