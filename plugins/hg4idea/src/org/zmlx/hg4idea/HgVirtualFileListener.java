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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.*;

import java.io.File;
import java.util.Arrays;

import static com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
import static com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;

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
    final VirtualFile newFile = event.getFile();
    FilePath newPath = getFilePath(newFile);
    VirtualFile newRepo = VcsUtil.getVcsRootFor(project, newFile);
    boolean newFileProcessable = newRepo != null && VcsUtil.isFileForVcs(newFile, project, vcs) && isFileProcessable(newFile);

    final VirtualFile oldFile = event.getOriginalFile();
    FilePath oldPath = getFilePath(oldFile);
    VirtualFile oldRepo = VcsUtil.getVcsRootFor(project, oldFile);
    boolean oldFileProcessable = oldRepo != null && VcsUtil.isFileForVcs(oldFile, project, vcs) && isFileProcessable(oldFile);

    if (newFileProcessable && oldFileProcessable && oldRepo.equals(newRepo)) {
      copyFile(newRepo, oldPath, newPath);
      markDirty(newPath);
    } else if (newFileProcessable) {
      addFile(newRepo, newPath, false);
      markDirty(newPath);
    }
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    VirtualFile oldParent = event.getOldParent();
    String fileName = event.getFileName();

    FilePath oldPath = VcsUtil.getFilePath(new File(new File(oldParent.getPath()), fileName));
    VirtualFile oldRepo = VcsUtil.getVcsRootFor(project, oldPath);
    boolean oldFileProcessable = oldRepo != null && VcsUtil.isFileForVcs(oldPath, project, vcs) && isFileProcessable(oldPath);

    VirtualFile newFile = event.getFile();
    FilePath newPath = getFilePath(newFile);
    VirtualFile newRepo = VcsUtil.getVcsRootFor(project, newFile);
    boolean newFileProcessable = newRepo != null && VcsUtil.isFileForVcs(newFile, project, vcs) && isFileProcessable(newFile);

    if (newFileProcessable && oldFileProcessable && oldRepo.equals(newRepo)) {
      moveFile(oldRepo, newRepo, oldPath, newPath);
    } else {
      HgFileStatusEnum oldStatus = getStatus(oldRepo, oldPath);
      boolean silent = oldStatus != HgFileStatusEnum.UNVERSIONED;

      if (oldFileProcessable) {
        deleteFile(oldRepo, oldPath, silent);
      }

      if (newFileProcessable) {
        addFile(newRepo, newPath, silent);
      }
    }

    markDirty(oldPath);
    markDirty(newPath);
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

    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (repo == null) {
      return;
    }

    FilePath path = getFilePath(file);
    addFile(repo, path, false);
    markDirty(path);
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    if (event.isFromRefresh()) {
      return;
    }

    final VirtualFile file = event.getFile();

    if (!shouldProcess(file)) {
      return;
    }

    VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
    if (repo == null) {
      return;
    }

    FilePath path = getFilePath(file);
    deleteFile(repo, path, false);
    markDirty(path);
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      fileRenamed(event);
    }
  }

  private void fileRenamed(VirtualFilePropertyEvent event) {
    String oldName = (String) event.getOldValue();
    VirtualFile oldParent = event.getParent();

    FilePath oldPath = VcsUtil.getFilePath(new File(new File(oldParent.getPath()), oldName));
    VirtualFile oldRepo = VcsUtil.getVcsRootFor(project, oldPath);
    boolean oldFileProcessable = oldRepo != null && VcsUtil.isFileForVcs(oldPath, project, vcs) && isFileProcessable(oldPath);

    VirtualFile newFile = event.getFile();
    FilePath newPath = getFilePath(newFile);
    VirtualFile newRepo = VcsUtil.getVcsRootFor(project, newFile);
    boolean newFileProcessable = newRepo != null && VcsUtil.isFileForVcs(newFile, project, vcs) && isFileProcessable(newFile);

    if (newFileProcessable && oldFileProcessable && oldRepo.equals(newRepo)) {
      renameFile(newRepo, oldPath, newPath);
    } else {
      if (oldFileProcessable) {
        deleteFile(oldRepo, oldPath, false);
      }

      if (newFileProcessable) {
        addFile(newRepo, newPath, false);
      }
    }
    markDirty(oldPath);
    markDirty(newPath);
  }

  private void markDirty(final FilePath path) {
    HgUtil.markDirectoryDirty(project, path.getParentPath());
  }

  private FilePath getFilePath(VirtualFile file) {
    return VcsUtil.getFilePath(file.getPath());
  }

  private void addFile(@NotNull VirtualFile repo, @NotNull FilePath path, boolean silent) {
    if (silent || checkAdd(path)) {
      new HgAddCommand(project).execute(new HgFile(repo, path));
    }
  }

  private void copyFile(VirtualFile repo, FilePath oldPath, FilePath newPath) {
    if (checkAdd(newPath)) {
      HgCopyCommand command = new HgCopyCommand(project);
      HgFile source = new HgFile(repo, oldPath);
      HgFile target = new HgFile(repo, newPath);
      command.execute(source, target);
    }
  }

  private void moveFile(@NotNull VirtualFile repo, VirtualFile newRepo, @NotNull FilePath oldPath, @NotNull FilePath newPath) {
    HgMoveCommand command = new HgMoveCommand(project);
    HgFile source = new HgFile(repo, oldPath);
    HgFile target = new HgFile(repo, newPath);
    command.execute(source, target);
  }

  private void renameFile(@NotNull VirtualFile repo, @NotNull FilePath oldPath, @NotNull FilePath newPath) {
    HgMoveCommand command = new HgMoveCommand(project);
    HgFile source = new HgFile(repo, oldPath);
    HgFile target = new HgFile(repo, newPath);
    command.execute(source, target);
  }

  private boolean checkAdd(FilePath path) {
    String title = HgVcsMessages.message("hg4idea.add.confirmation.title");
    String message = HgVcsMessages.message("hg4idea.add.confirmation.body", path.getPath());

    VcsShowConfirmationOption option = ProjectLevelVcsManager.getInstance(project)
      .getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);

    boolean processAdd = false;
    if (DO_ACTION_SILENTLY == option.getValue()) {
      processAdd = true;
    } else if (SHOW_CONFIRMATION == option.getValue()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
      processAdd = null != helper.selectFilePathsToProcess(
        Arrays.asList(path), title, null, title, message, option
      );
    }
    return processAdd;
  }

  private void deleteFile(@NotNull VirtualFile repo, @NotNull FilePath path, boolean silent) {
    HgFileStatusEnum status = getStatus(repo, path);
    if (status == HgFileStatusEnum.UNVERSIONED || status == HgFileStatusEnum.IGNORED) {
      return;
    }

    String title = HgVcsMessages.message("hg4idea.delete.confirmation.title");
    String message = HgVcsMessages.message("hg4idea.delete.confirmation.body", path.getPath());

    boolean processDelete = false;

    VcsShowConfirmationOption option = ProjectLevelVcsManager.getInstance(project)
      .getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    if (DO_ACTION_SILENTLY == option.getValue() || status == HgFileStatusEnum.ADDED || silent) {
      processDelete = true;
    } else if (SHOW_CONFIRMATION == option.getValue()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
      processDelete = null != helper.selectFilePathsToProcess(
        Arrays.asList(path), title, null, title, message, option
      );
    }

    if (processDelete) {
      new HgRemoveCommand(project).execute(new HgFile(repo, path));
    }
  }

  private HgFileStatusEnum getStatus(VirtualFile repo, FilePath file) {
    HgStatusCommand status = new HgStatusCommand(project);
    HgChange change = status.execute(repo, file.getPath());
    return change != null ? change.getStatus() : HgFileStatusEnum.UNVERSIONED;
  }

  private boolean shouldProcess(VirtualFile file) {
    return VcsUtil.isFileForVcs(file, project, vcs) && isFileProcessable(file);
  }

  private boolean isFileProcessable(VirtualFile file) {
    if (file == null) {
      return false;
    }
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    return !FileTypeManager.getInstance().isFileIgnored(file.getName())
      || !changeListManager.isIgnoredFile(file);
  }

  private boolean isFileProcessable(FilePath file) {
    if (file == null) {
      return false;
    }
    return !FileTypeManager.getInstance().isFileIgnored(file.getName());
  }
}
