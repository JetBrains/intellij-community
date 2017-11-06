/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CvsStorageSupportingDeletionComponent extends CvsStorageComponent implements CommandListener {
  private static final Logger LOG = Logger.getInstance(CvsStorageSupportingDeletionComponent.class);
  private Project myProject;

  private DeletedCVSDirectoryStorage myDeletedStorage;
  private DeleteHandler myDeleteHandler = null;
  private AddHandler myAddHandler = null;
  private CvsFileOperationsHandler myFileOperationsHandler;

  private int myCommandLevel = 0;

  private boolean myAnotherProjectCommand = false;
  static final Key<AbstractVcs> FILE_VCS = new Key<>("File VCS");

  @Override
  public void commandStarted(CommandEvent event) {
    myCommandLevel++;
    if (myCommandLevel == 1) {
      myAnotherProjectCommand = (event.getProject() == null) == (event.getProject() == myProject);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Started" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
    }
  }

  @Override
  public void commandFinished(CommandEvent event) {
    myCommandLevel--;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Finished" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
    }
    execute();
  }

  @Override
  public void init(Project project, boolean sync) {
    myProject = project;
    initializeDeletedStorage();
    VirtualFileManager.getInstance().addVirtualFileListener(this);
    CvsEntriesManager.getInstance().registerAsVirtualFileListener();
    CommandProcessor.getInstance().addCommandListener(this);
    myFileOperationsHandler = new CvsFileOperationsHandler(project, this);
    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myFileOperationsHandler);
    myIsActive = true;
  }

  @Override
  public void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
    CvsEntriesManager.getInstance().unregisterAsVirtualFileListener();
    CommandProcessor.getInstance().removeCommandListener(this);
    LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myFileOperationsHandler);
    myFileOperationsHandler = null;
    myIsActive = false;
    myProject = null;
  }

  public DeleteHandler getDeleteHandler() {
    if (myDeleteHandler == null) {
      myDeleteHandler = myDeletedStorage.createDeleteHandler(myProject, this);
    }
    return myDeleteHandler;
  }

  private boolean shouldProcessEvent(VirtualFileEvent event, boolean parentShouldBeUnderCvs) {
    if (myAnotherProjectCommand) {
      return false;
    }
    final VirtualFile file = event.getFile();
    if (disabled(file) || event.isFromRefresh() || isStorageEvent(event) || !isUnderCvsManagedModuleRoot(file)) {
      return false;
    }
    return !parentShouldBeUnderCvs || parentIsUnderCvs(file);
  }

  private static boolean isStorageEvent(VirtualFileEvent event) {
    return event.getRequestor() instanceof DeletedCVSDirectoryStorage;
  }

  private static boolean parentIsUnderCvs(VirtualFile file) {
    return CvsUtil.fileIsUnderCvs(file.getParent());
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    fileCreated(event);
  }

  private boolean processMoveOrRename() {
    return myDeleteHandler != null;
  }

  private AbstractVcs getFileVcs(VirtualFile file) {
    final AbstractVcs storedData = file.getUserData(FILE_VCS);
    if (storedData != null) {
      return storedData;
    }
    return ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
  }

  private boolean isUnderCvsManagedModuleRoot(VirtualFile file) {
    return getFileVcs(file) == CvsVcs2.getInstance(myProject);
  }

  private boolean disabled(VirtualFile file) {
    return ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) != CvsVcs2.getInstance(myProject);
  }

  @Override
  public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
    if (!event.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    if (!CvsUtil.fileIsUnderCvs(event.getFile())) return;
    beforeFileDeletion(event);
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (processMoveOrRename()) {
      fileDeleted(event);
      fileCreated(event);
    }
  }

  @Override
  public void fileCreated(@NotNull final VirtualFileEvent event) {
    if (!shouldProcessEvent(event, false)) return;
    final Project project = myProject;
    if (project == null) return;    // already disposed
    
    final VirtualFile file = event.getFile();
    if (myDeleteHandler != null) {
      myDeleteHandler.removeDeletedRoot(file);
    }
    myDeletedStorage.checkNeedForPurge(VfsUtilCore.virtualToIoFile(file));
    deleteIfAdminDirCreated(file);
    getAddHandler().addFile(file);

    execute();
  }

  public AddHandler getAddHandler() {
    if (myAddHandler == null) {
      myAddHandler = new AddHandler(myProject, this);
    }
    return myAddHandler;
  }

  private void execute() {
    if (myCommandLevel > 0) {
      return;
    }
    //myDeletedStorage.sync();
    if (!myAnotherProjectCommand) {
      if (myDeleteHandler != null) {
        myDeleteHandler.execute();
      }
      if (myAddHandler != null) {
        myAddHandler.execute();
      }
    }
    myAnotherProjectCommand = false;
    myDeleteHandler = null;
    myAddHandler = null;
  }

  private void initializeDeletedStorage() {
    final File storageRoot = getStorageRoot();
    storageRoot.mkdirs();
    myDeletedStorage = new DeletedCVSDirectoryStorage(storageRoot);
  }

  private static File getStorageRoot() {
    //noinspection HardCodedStringLiteral
    return new File(PathManager.getSystemPath(), "CVS-TO-DELETE");
  }

  public void sync() {
    //myDeletedStorage.sync();
  }

  public static CvsStorageComponent getInstance(Project project) {
    return ServiceManager.getService(project, CvsStorageComponent.class);
  }

  @Override
  public void deleteIfAdminDirCreated(VirtualFile addedFile) {
    myDeletedStorage.deleteIfAdminDirCreated(addedFile);
  }
}
