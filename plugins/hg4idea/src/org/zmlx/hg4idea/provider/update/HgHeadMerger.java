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
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgChange;
import org.zmlx.hg4idea.command.HgCommandResult;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgStatusCommand;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

final class HgHeadMerger {

  private static final Logger LOG = Logger.getInstance(HgHeadMerger.class.getName());
  private final Project project;
  private final HgMergeCommand hgMergeCommand;

  public HgHeadMerger(Project project, @NotNull HgMergeCommand hgMergeCommand) {
    this.project = project;
    this.hgMergeCommand = hgMergeCommand;
  }

  public HgCommandResult merge(VirtualFile repo, UpdatedFiles updatedFiles,
    VcsRevisionNumber revisionNumber) throws VcsException {

    HgCommandResult commandResult = ensureSuccess(hgMergeCommand.execute());

    try {
      HgUtil.markDirectoryDirty(project, repo);
    }
    catch (InvocationTargetException e) {
      throwException(e);
    }
    catch (InterruptedException e) {
      throwException(e);
    }

    HgStatusCommand hgStatusCommand = new HgStatusCommand(project);
    hgStatusCommand.setIncludeIgnored(false);
    hgStatusCommand.setIncludeUnknown(false);
    Set<HgChange> changes = hgStatusCommand.execute(repo);
    if (!changes.isEmpty()) {
      for (HgChange change : changes) {
        HgFile afterFile = change.afterFile();
        HgFile beforeFile = change.beforeFile();
        String fileGroupId = null;
        String filePath = null;
        if (afterFile != null && beforeFile != null) {
          fileGroupId = FileGroup.MODIFIED_ID;
          filePath = afterFile.getFile().getAbsolutePath();
        } else if (beforeFile != null) {
          fileGroupId = FileGroup.LOCALLY_REMOVED_ID;
          filePath = beforeFile.getFile().getAbsolutePath();
        } else if (afterFile != null) {
          fileGroupId = FileGroup.LOCALLY_ADDED_ID;
          filePath = afterFile.getFile().getAbsolutePath();
        }
        if (fileGroupId != null && filePath != null) {
          updatedFiles.getGroupById(fileGroupId).add(filePath, HgVcs.VCS_NAME, revisionNumber);
        }
      }
    }
    return commandResult;
  }

  private static void throwException(Exception e) throws VcsException {
    String msg = "Exception during marking directory dirty: " + e;
    LOG.info(msg, e);
    throw new VcsException(msg);
  }

}
