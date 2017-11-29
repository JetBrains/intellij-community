/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class CvsFileOperationsHandler implements LocalFileOperationsHandler {
  private final Project myProject;
  private final CvsStorageSupportingDeletionComponent myComponent;
  private boolean myInternalDelete = false;

  public CvsFileOperationsHandler(final Project project, final CvsStorageSupportingDeletionComponent component) {
    myProject = project;
    myComponent = component;
  }

  public boolean delete(final VirtualFile file) throws IOException {
    return processDeletedFile(file);
  }

  private boolean processDeletedFile(final VirtualFile file) throws IOException {
    if (myInternalDelete) return false;
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs != CvsVcs2.getInstance(myProject)) return false;
    file.putUserData(CvsStorageSupportingDeletionComponent.FILE_VCS, vcs);
    if (!CvsUtil.fileIsUnderCvs(file)) return false;
    myComponent.getDeleteHandler().addDeletedRoot(file);
    if (file.isDirectory()) {
      myInternalDelete = true;
      try {
        deleteFilesInVFS(file);
      }
      finally {
        myInternalDelete = false;
      }
      return true;
    }
    return false;
  }

  private void deleteFilesInVFS(final VirtualFile file) throws IOException {
    for(VirtualFile child: file.getChildren()) {
      if (child.isDirectory()) {
        if (DeletedCVSDirectoryStorage.isAdminDir(child)) continue;
        deleteFilesInVFS(child);
      }
      else {
        child.delete(this);
      }
    }
  }

  public boolean move(final VirtualFile file, final VirtualFile toDir) throws IOException {
    return doMoveRename(file, toDir, file.getName());
  }

  @Nullable
  public File copy(final VirtualFile file, final VirtualFile toDir, final String copyName) {
    return null;
  }

  public boolean rename(final VirtualFile file, final String newName) throws IOException {
    return doMoveRename(file, file.getParent(), newName);
  }

  private boolean doMoveRename(final VirtualFile file, final VirtualFile newParent, final String newName) throws IOException {
    if (!CvsUtil.fileIsUnderCvs(file)) return false;
    if (newParent == null) return false;
    final File newFile = new File(newParent.getPath(), newName);
    myComponent.getDeleteHandler().addDeletedRoot(file);
    if (!file.isDirectory()) {
      myComponent.getAddHandler().addFile(newFile);
      return false;
    }
    newFile.mkdir();
    copyDirectoryStructure(file, newFile);
    myComponent.getAddHandler().addFile(newFile);
    final DocumentReference ref = DocumentReferenceManager.getInstance().create(file);
    UndoManager.getInstance(myProject).nonundoableActionPerformed(ref, false);
    return true;
  }

  private static void copyDirectoryStructure(final VirtualFile file, final File newFile) {
    for(VirtualFile child: file.getChildren()) {
      final File newChild = new File(newFile, child.getName());
      if (child.isDirectory()) {
        if (DeletedCVSDirectoryStorage.isAdminDir(child)) continue;
        newChild.mkdir();
        copyDirectoryStructure(child, newChild);
      }
      else {
        new File(child.getPath()).renameTo(newChild);
      }
    }
  }

  public boolean createFile(final VirtualFile dir, final String name) {
    return false;
  }

  public boolean createDirectory(final VirtualFile dir, final String name) {
    return false;
  }

  public void afterDone(final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker) {}
}
