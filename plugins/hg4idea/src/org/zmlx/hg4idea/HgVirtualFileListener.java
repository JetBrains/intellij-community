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
package org.zmlx.hg4idea;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import static com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
import static com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.command.HgAddCommand;
import org.zmlx.hg4idea.command.HgCopyCommand;
import org.zmlx.hg4idea.command.HgMoveCommand;
import org.zmlx.hg4idea.command.HgRemoveCommand;

import java.io.File;
import java.util.Arrays;

public class HgVirtualFileListener extends VirtualFileAdapter {

  private final Project project;
  private final AbstractVcs vcs;

  public HgVirtualFileListener(Project project, AbstractVcs vcs) {
    this.project = project;
    this.vcs = vcs;
  }

  @Override
  public void fileCopied(VirtualFileCopyEvent event) {
    if (event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (!VcsUtil.isFileForVcs(file, project, vcs)) {
      return;
    }
    if (!isFileProcessable(file)) {
      return;
    }

    VirtualFile newFile = event.getFile();
    VirtualFile oldFile = event.getOriginalFile();
    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (repo == null) {
      return;
    }

    HgCopyCommand command = new HgCopyCommand(project);
    HgFile source = new HgFile(repo, new File(oldFile.getPath()));
    HgFile target = new HgFile(repo, new File(newFile.getPath()));
    command.execute(source, target);
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    if (event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (!VcsUtil.isFileForVcs(file, project, vcs)) {
      return;
    }
    if (!isFileProcessable(file) || file.isDirectory()) {
      return;
    }

    String title = HgVcsMessages.message("hg4idea.add.confirmation.title");
    String message = HgVcsMessages.message("hg4idea.add.confirmation.body", file.getPath());

    VcsShowConfirmationOption option = ProjectLevelVcsManager.getInstance(project)
      .getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);

    boolean processAdd = false;
    if (DO_ACTION_SILENTLY == option.getValue()) {
      processAdd = true;
    } else if (SHOW_CONFIRMATION == option.getValue()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
      processAdd = null != helper.selectFilesToProcess(
        Arrays.asList(file), title, null, title, message, option
      );
    }
    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (processAdd && repo != null) {
      new HgAddCommand(project).execute(new HgFile(repo, new File(file.getPath())));
    }
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    if (event.isFromRefresh()) {
      return;
    }

    final VirtualFile file = event.getFile();
    if (!VcsUtil.isFileForVcs(file, project, vcs)) {
      return;
    }

    if (!isFileProcessable(file)) {
      return;
    }

    FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    if (status == FileStatus.UNKNOWN || status == FileStatus.IGNORED) {
      return;
    }

    String title = HgVcsMessages.message("hg4idea.delete.confirmation.title");
    String message = HgVcsMessages.message("hg4idea.delete.confirmation.body", file.getPath());

    VcsShowConfirmationOption option = ProjectLevelVcsManager.getInstance(project)
      .getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    boolean processDelete = false;
    if (DO_ACTION_SILENTLY == option.getValue()) {
      processDelete = true;
    } else if (SHOW_CONFIRMATION == option.getValue()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
      processDelete = null != helper.selectFilesToProcess(
        Arrays.asList(file), title, null, title, message, option
      );
    }
    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (processDelete && repo != null) {
      new HgRemoveCommand(project).execute(new HgFile(repo, new File(file.getPath())));
    }
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    VirtualFile newParent = event.getNewParent();
    VirtualFile oldParent = event.getOldParent();
    String fileName = event.getFileName();
    VirtualFile repo = VcsUtil.getVcsRootFor(project, event.getFile());
    if (repo == null) {
      return;
    }
    HgMoveCommand command = new HgMoveCommand(project);
    HgFile source = new HgFile(repo, new File(oldParent.getPath(), fileName));
    HgFile target = new HgFile(repo, new File(newParent.getPath(), fileName));
    command.execute(source, target);
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      fileRenamed(event);
    }
  }

  private void fileRenamed(VirtualFilePropertyEvent event) {
    VirtualFile file = event.getFile();
    VirtualFile parent = file.getParent();
    String oldName = (String) event.getOldValue();
    String newName = (String) event.getNewValue();
    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (repo == null || parent == null) {
      return;
    }
    HgMoveCommand command = new HgMoveCommand(project);
    HgFile source = new HgFile(repo, new File(parent.getPath(), oldName));
    HgFile target = new HgFile(repo, new File(parent.getPath(), newName));
    command.execute(source, target);
  }

  private boolean isFileProcessable(VirtualFile file) {
    if (file == null) {
      return false;
    }
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    return !FileTypeManager.getInstance().isFileIgnored(file.getName())
      || !changeListManager.isIgnoredFile(file);
  }

}
