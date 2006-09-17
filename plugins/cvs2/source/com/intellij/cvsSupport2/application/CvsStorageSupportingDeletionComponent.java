package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ModuleLevelVcsManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.CvsBundle;

import java.io.File;
import java.io.IOException;

public class CvsStorageSupportingDeletionComponent extends CvsStorageComponent
  implements CommandListener {
  private static final Logger LOG = Logger.getInstance("#" + CvsStorageSupportingDeletionComponent.class.getName());
  private Project myProject;

  private DeletedCVSDirectoryStorage myDeletedStorage;
  private DeleteHandler myDeleteHandler = null;
  private AddHandler myAddHandler = null;

  private int myCommandLevel = 0;

  private boolean myAnotherProjectCommand = false;
  private static final Key<Module> FILE_MODULE = new Key<Module>("File Module");

  public void commandStarted(CommandEvent event) {
    myCommandLevel++;
    if (myCommandLevel == 1) {
      myAnotherProjectCommand = (event.getProject() != null) != (event.getProject() == myProject);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Started" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
    }
  }

  public void beforeCommandFinished(CommandEvent event) {

  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }

  public void commandFinished(CommandEvent event) {
    myCommandLevel--;
    if (myCommandLevel == 0) myAnotherProjectCommand = false;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Finished" + event.getCommandName() + ", commandLevel: " + myCommandLevel);
    }
    execute();
  }

  public void init(Project project, boolean sync) {
    myProject = project;
    initializeDeletedStorage();
    VirtualFileManager.getInstance().addVirtualFileListener(this);
    CvsEntriesManager.getInstance().registerAsVirtualFileListener();
    CommandProcessor.getInstance().addCommandListener(this);
  }

  public void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
    CvsEntriesManager.getInstance().unregisterAsVirtualFileListener();
    CommandProcessor.getInstance().removeCommandListener(this);
    myProject = null;
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    file.putUserData(FILE_MODULE, VfsUtil.getModuleForFile(myProject, file));
    if (!CvsUtil.fileIsUnderCvs(file)) return;
    if (!shouldProcessEvent(event, false)) return;
    LOG.info("Preserving CVS info from " + file);
    try {
      if (event.getRequestor() != myDeletedStorage) {
        if (myDeleteHandler == null) {
          myDeleteHandler = myDeletedStorage.createDeleteHandler(myProject, this);
        }
        myDeleteHandler.addDeletedRoot(file);
        myDeletedStorage.saveCVSInfo(VfsUtil.virtualToIoFile(file));
      }
    }
    catch (final IOException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(CvsBundle.message("message.error.cannot.restore.cvs.admin.directories", e.getLocalizedMessage()),
                                     CvsBundle.message("message.error.cannot.restore.cvs.admin.directories.title"), Messages.getErrorIcon());
        }
      });
    }
  }

  public void fileDeleted(VirtualFileEvent event) {
    try {
      if (!shouldProcessEvent(event, true)) return;
      execute();
    }
    finally {
      event.getFile().putUserData(FILE_MODULE, null);
    }
  }

  private boolean shouldProcessEvent(VirtualFileEvent event, boolean parentShouldBeUnderCvs) {
    if (myAnotherProjectCommand) return false;
    VirtualFile file = event.getFile();
    if (disabled(file)) return false;
    if (event.isFromRefresh()) return false;
    if (isStorageEvent(event)) return false;
    if (!isUnderCvsManagedModuleRoot(file)) return false;
    return !(parentShouldBeUnderCvs && !parentIsUnderCvs(file));
  }

  private static boolean isStorageEvent(VirtualFileEvent event) {
    return event.getRequestor() instanceof DeletedCVSDirectoryStorage;
  }

  private static boolean parentIsUnderCvs(VirtualFile file) {
    return CvsUtil.fileIsUnderCvs(file.getParent());
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    LOG.assertTrue(myCommandLevel > 0);
    beforeFileDeletion(event);
  }

  public void fileMoved(VirtualFileMoveEvent event) {
    if (processMoveOrRename()) {
      fileDeleted(event);
    }
    fileCreated(event);
  }

  private boolean processMoveOrRename() {
    return myDeleteHandler != null;
  }

  private Module getFileModule(VirtualFile file) {
    Module storedData = file.getUserData(FILE_MODULE);
    if (storedData != null) {
      return storedData;
    }
    else {
      return VfsUtil.getModuleForFile(myProject, file);
    }
  }

  private boolean isUnderCvsManagedModuleRoot(VirtualFile file) {
    Module fileModule = getFileModule(file);
    if (fileModule == null) {
      return false;
    }
    else {
      return ModuleLevelVcsManager.getInstance(fileModule).getActiveVcs() == CvsVcs2.getInstance(myProject);
    }
  }

  public void purge() {
    try {
      myDeletedStorage.purgeDirsWithNoEntries();
    }
    catch (IOException e) {
      LOG.info("Purging saved CVS storage: ", e);
    }
  }

  private boolean disabled(VirtualFile file) {
    return ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) !=
           CvsVcs2.getInstance(myProject);
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
    if (!event.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    if (!CvsUtil.fileIsUnderCvs(event.getFile())) return;
    beforeFileDeletion(event);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    if (processMoveOrRename()) {
      fileDeleted(event);
      fileCreated(event);
    }
  }

  public void fileCreated(final VirtualFileEvent event) {
    if (!shouldProcessEvent(event, false)) return;
    final VirtualFile file = event.getFile();
    myDeletedStorage.checkNeedForPurge(VfsUtil.virtualToIoFile(file));
    deleteIfAdminDirCreated(file);
    if (myAddHandler == null) myAddHandler = new AddHandler(myProject, this);
    myAddHandler.addFile(file);

    execute();
  }

  private void execute() {

    if (myCommandLevel > 0) return;
    myDeletedStorage.sync();
    if (myDeleteHandler != null) myDeleteHandler.execute();
    if (myAddHandler != null) myAddHandler.execute();

    myDeleteHandler = null;
    myAddHandler = null;
  }

  private void initializeDeletedStorage() {
    File storageRoot = getStorageRoot();
    storageRoot.mkdirs();
    myDeletedStorage = new DeletedCVSDirectoryStorage(storageRoot);
  }

  private static File getStorageRoot() {
    //noinspection HardCodedStringLiteral
    return new File(PathManager.getSystemPath(), "CVS-TO-DELETE");
  }

  public void sync() {
    myDeletedStorage.sync();
  }

  public static CvsStorageComponent getInstance(Project project) {
    return project.getComponent(CvsStorageSupportingDeletionComponent.class);
  }

  public void deleteIfAdminDirCreated(VirtualFile addedFile) {
    myDeletedStorage.deleteIfAdminDirCreated(addedFile);
  }
}
