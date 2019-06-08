// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CvsStorageSupportingDeletionComponent implements VirtualFileListener {
  private static final Logger LOG = Logger.getInstance(CvsStorageSupportingDeletionComponent.class);
  private boolean myIsActive;
  private final Project myProject;

  private DeletedCVSDirectoryStorage myDeletedStorage;
  private DeleteHandler myDeleteHandler;
  private AddHandler myAddHandler;
  private CvsFileOperationsHandler myFileOperationsHandler;

  private int myCommandLevel;

  private boolean myAnotherProjectCommand;
  static final Key<AbstractVcs> FILE_VCS = new Key<>("File VCS");

  private Disposable myListenerDisposable = Disposer.newDisposable();

  public CvsStorageSupportingDeletionComponent(Project project) {
    myProject = project;
  }

  @NotNull
  public static CvsStorageSupportingDeletionComponent getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CvsStorageSupportingDeletionComponent.class);
  }

  public void activate() {
    initializeDeletedStorage();
    VirtualFileManager.getInstance().addVirtualFileListener(this, myListenerDisposable);
    CvsEntriesManager.getInstance().activate();
    myProject.getMessageBus().connect(myListenerDisposable).subscribe(CommandListener.TOPIC, new CommandListener() {
      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        myCommandLevel++;
        if (myCommandLevel == 1) {
          myAnotherProjectCommand = (event.getProject() == null) == (event.getProject() == myProject);
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("Started" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
        }
      }

      @Override
      public void commandFinished(@NotNull CommandEvent event) {
        myCommandLevel--;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Finished" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
        }
        execute();
      }
    });
    myFileOperationsHandler = new CvsFileOperationsHandler(myProject, this);
    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myFileOperationsHandler);
    myIsActive = true;
  }

  public void deactivate() {
    Disposable listenerDisposable = myListenerDisposable;
    if (listenerDisposable == null) {
      return;
    }

    myListenerDisposable = null;
    Disposer.dispose(listenerDisposable);

    CvsEntriesManager.getInstance().deactivate();
    LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myFileOperationsHandler);
    myFileOperationsHandler = null;
    myIsActive = false;
  }

  @NotNull
  DeleteHandler getDeleteHandler() {
    if (myDeleteHandler == null) {
      myDeleteHandler = myDeletedStorage.createDeleteHandler(myProject, this);
    }
    return myDeleteHandler;
  }

  private boolean shouldProcessEvent(VirtualFileEvent event) {
    if (myAnotherProjectCommand) {
      return false;
    }
    final VirtualFile file = event.getFile();
    return !disabled(file) && !event.isFromRefresh() && !isStorageEvent(event) && isUnderCvsManagedModuleRoot(file);
  }

  boolean getIsActive() {
    return myIsActive;
  }

  private static boolean isStorageEvent(VirtualFileEvent event) {
    return event.getRequestor() instanceof DeletedCVSDirectoryStorage;
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
    if (!shouldProcessEvent(event)) return;
    if (!myIsActive) return;    // already disposed

    final VirtualFile file = event.getFile();
    if (myDeleteHandler != null) {
      myDeleteHandler.removeDeletedRoot(file);
    }
    myDeletedStorage.checkNeedForPurge(VfsUtilCore.virtualToIoFile(file));
    deleteIfAdminDirCreated(file);
    getAddHandler().addFile(file);

    execute();
  }

  @NotNull
  AddHandler getAddHandler() {
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
    FileUtilRt.createDirectory(storageRoot);
    myDeletedStorage = new DeletedCVSDirectoryStorage(storageRoot);
  }

  private static File getStorageRoot() {
    return new File(PathManager.getSystemPath(), "CVS-TO-DELETE");
  }

  public void sync() {
    //myDeletedStorage.sync();
  }

  public void deleteIfAdminDirCreated(VirtualFile addedFile) {
    myDeletedStorage.deleteIfAdminDirCreated(addedFile);
  }
}
