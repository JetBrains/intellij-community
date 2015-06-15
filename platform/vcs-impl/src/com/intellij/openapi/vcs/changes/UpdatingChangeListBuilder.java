/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class UpdatingChangeListBuilder implements ChangelistBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdatingChangeListBuilder");
  private final ChangeListWorker myChangeListWorker;
  private final FileHolderComposite myComposite;
  // todo +-
  private final Getter<Boolean> myDisposedGetter;
  private VcsDirtyScope myScope;
  private FoldersCutDownWorker myFoldersCutDownWorker;
  private final IgnoredFilesComponent myIgnoredFilesComponent;
  private final ProjectLevelVcsManager myVcsManager;
  private final ChangeListManagerGate myGate;
  private Factory<JComponent> myAdditionalInfo;

  UpdatingChangeListBuilder(final ChangeListWorker changeListWorker,
                            final FileHolderComposite composite,
                            final Getter<Boolean> disposedGetter,
                            final IgnoredFilesComponent ignoredFilesComponent, final ChangeListManagerGate gate) {
    myChangeListWorker = changeListWorker;
    myComposite = composite;
    myDisposedGetter = disposedGetter;
    myIgnoredFilesComponent = ignoredFilesComponent;
    myGate = gate;
    myVcsManager = ProjectLevelVcsManager.getInstance(changeListWorker.getProject());
  }

  private void checkIfDisposed() {
    if (myDisposedGetter.get()) throw new ChangeListManagerImpl.DisposedException();
  }

  public void setCurrent(final VcsDirtyScope scope, final FoldersCutDownWorker foldersWorker) {
    myScope = scope;
    myFoldersCutDownWorker = foldersWorker;
  }

  public void processChange(final Change change, VcsKey vcsKey) {
    processChangeInList(change, (ChangeList)null, vcsKey);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList, final VcsKey vcsKey) {
    checkIfDisposed();

    LOG.debug("[processChangeInList-1] entering, cl name: " + ((changeList == null) ? null: changeList.getName()) +
              " change: " + ChangesUtil.getFilePath(change).getPath());
    final String fileName = ChangesUtil.getFilePath(change).getName();
    if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
      LOG.debug("[processChangeInList-1] file type ignored");
      return;
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (ChangeListManagerImpl.isUnder(change, myScope)) {
          if (changeList != null) {
            LOG.debug("[processChangeInList-1] to add change to cl");
            myChangeListWorker.addChangeToList(changeList.getName(), change, vcsKey);
          }
          else {
            LOG.debug("[processChangeInList-1] to add to corresponding list");
            myChangeListWorker.addChangeToCorrespondingList(change, vcsKey);
          }
        }
        else {
          LOG.debug("[processChangeInList-1] not under scope");
        }
      }
    });
  }

  public void processChangeInList(final Change change, final String changeListName, VcsKey vcsKey) {
    checkIfDisposed();

    LocalChangeList list = null;
    if (changeListName != null) {
      list = myChangeListWorker.getCopyByName(changeListName);
      if (list == null) {
        list = myGate.addChangeList(changeListName, null);
      }
    }
    processChangeInList(change, list, vcsKey);
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
    myChangeListWorker.removeRegisteredChangeFor(path);
  }

  private boolean isIgnoredByVcs(final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        checkIfDisposed();
        return myVcsManager.isIgnored(file);
      }
    });
  }

  public void processUnversionedFile(final VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("processUnversionedFile " + file);
    }
    if (file == null) return;
    checkIfDisposed();
    if (isIgnoredByVcs(file)) return;
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      if (myIgnoredFilesComponent.isIgnoredFile(file)) {
        myComposite.getIgnoredFileHolder().addFile(file);
      }
      else if (myComposite.getIgnoredFileHolder().containsFile(file)) {
        // does not need to add: parent dir is already added
      }
      else {
        myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).addFile(file);
      }
      // if a file was previously marked as switched through recursion, remove it from switched list
      myChangeListWorker.removeSwitched(file);
    }
  }

  public void processLocallyDeletedFile(final FilePath file) {
    processLocallyDeletedFile(new LocallyDeletedChange(file));
  }

  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
    checkIfDisposed();
    final FilePath file = locallyDeletedChange.getPath();
    if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
    if (myScope.belongsTo(file)) {
      myChangeListWorker.addLocallyDeleted(locallyDeletedChange);
    }
  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {
    if (file == null) return;
    checkIfDisposed();
    if (isIgnoredByVcs(file)) return;
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("processModifiedWithoutCheckout " + file);
      }
      myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).addFile(file);
    }
  }

  public void processIgnoredFile(final VirtualFile file) {
    if (file == null) return;
    checkIfDisposed();
    if (isIgnoredByVcs(file)) return;
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      IgnoredFilesHolder ignoredFilesHolder = myComposite.getIgnoredFileHolder();
      if (ignoredFilesHolder instanceof IgnoredFilesCompositeHolder) {
        IgnoredFilesHolder holder = ((IgnoredFilesCompositeHolder)ignoredFilesHolder).getAppropriateIgnoredHolder();
        if (holder instanceof MapIgnoredFilesHolder) {
          ((MapIgnoredFilesHolder)holder).addByVcsChangeProvider(file);
          return;
        }
      }
      ignoredFilesHolder.addFile(file);
    }
  }

  public void processLockedFolder(final VirtualFile file) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      if (myFoldersCutDownWorker.addCurrent(file)) {
        myComposite.getVFHolder(FileHolder.HolderType.LOCKED).addFile(file);
      }
    }
  }

  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      ((LogicallyLockedHolder)myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).add(file, logicalLock);
    }
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
    if (file == null) return;
    checkIfDisposed();
    if (isIgnoredByVcs(file)) return;
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      myChangeListWorker.addSwitched(file, branch, recursive);
    }
  }

  public void processRootSwitch(VirtualFile file, String branch) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(VcsUtil.getFilePath(file))) {
      ((SwitchedFileHolder)myComposite.get(FileHolder.HolderType.ROOT_SWITCH)).addFile(file, branch, false);
    }
  }

  public boolean reportChangesOutsideProject() {
    return false;
  }

  @Override
  public void reportAdditionalInfo(String text) {
    reportAdditionalInfo(ChangesViewManager.createTextStatusFactory(text, true));
  }

  @Override
  public void reportAdditionalInfo(Factory<JComponent> infoComponent) {
    if (myAdditionalInfo == null) {
      myAdditionalInfo = infoComponent;
    }
  }

  public Factory<JComponent> getAdditionalInfo() {
    return myAdditionalInfo;
  }
}
