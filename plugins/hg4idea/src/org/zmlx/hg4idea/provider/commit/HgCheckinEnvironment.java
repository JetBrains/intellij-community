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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgAddCommand;
import org.zmlx.hg4idea.command.HgCommandException;
import org.zmlx.hg4idea.command.HgCommitCommand;
import org.zmlx.hg4idea.command.HgRemoveCommand;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class HgCheckinEnvironment implements CheckinEnvironment {

  private final Project project;

  public HgCheckinEnvironment(Project project) {
    this.project = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return HgVcsMessages.message("hg4idea.commit");
  }

  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    List<VcsException> exceptions = new LinkedList<VcsException>();
    for (Map.Entry<VirtualFile, List<HgFile>> entry : getFilesByRepository(changes).entrySet()) {
      HgCommitCommand command = new HgCommitCommand(project, entry.getKey(), preparedComment);
      command.setFiles(entry.getValue());
      try {
        command.execute();
      } catch (HgCommandException e) {
        exceptions.add(new VcsException(e));
      }
    }
    return exceptions;
  }

  public List<VcsException> commit(List<Change> changes,
    String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder) {
    return commit(changes, preparedComment);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    HgRemoveCommand command = new HgRemoveCommand(project);
    for (FilePath filePath : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
      if (vcsRoot == null) {
        continue;
      }
      command.execute(new HgFile(vcsRoot, filePath));
    }
    return null;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    HgAddCommand command = new HgAddCommand(project);
    for (VirtualFile file : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
      if (vcsRoot == null) {
        continue;
      }
      command.execute(new HgFile(vcsRoot, VfsUtil.virtualToIoFile(file)));
    }
    return null;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  private Map<VirtualFile, List<HgFile>> getFilesByRepository(List<Change> changes) {
    Map<VirtualFile, List<HgFile>> result = new HashMap<VirtualFile, List<HgFile>>();
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();

      FilePath filePath = null;
      if (afterRevision != null) {
        filePath = afterRevision.getFile();
      } else if (beforeRevision != null) {
        filePath = beforeRevision.getFile();
      }

      if (filePath == null) {
        continue;
      }

      VirtualFile repo = VcsUtil.getVcsRootFor(project, filePath);
      if (repo == null || filePath.isDirectory()) {
        continue;
      }

      List<HgFile> hgFiles = result.get(repo);
      if (hgFiles == null) {
        hgFiles = new LinkedList<HgFile>();
        result.put(repo, hgFiles);
      }

      hgFiles.add(new HgFile(repo, filePath));
    }
    return result;
  }

}
