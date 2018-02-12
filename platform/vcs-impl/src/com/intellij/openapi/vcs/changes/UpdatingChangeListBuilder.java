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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeListWorker.ChangeListUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class UpdatingChangeListBuilder implements ChangelistBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdatingChangeListBuilder");
  private final ChangeListUpdater myChangeListUpdater;
  private final FileHolderComposite myComposite;
  private final Getter<Boolean> myDisposedGetter;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;

  private VcsDirtyScope myScope;
  private FoldersCutDownWorker myFoldersCutDownWorker;

  private Factory<JComponent> myAdditionalInfo;

  UpdatingChangeListBuilder(final ChangeListUpdater changeListUpdater,
                            final FileHolderComposite composite,
                            final Getter<Boolean> disposedGetter,
                            final ChangeListManager changeListManager) {
    myChangeListUpdater = changeListUpdater;
    myComposite = composite;
    myDisposedGetter = disposedGetter;
    myChangeListManager = changeListManager;
    myVcsManager = ProjectLevelVcsManager.getInstance(changeListUpdater.getProject());
  }

  private void checkIfDisposed() {
    if (myDisposedGetter.get()) throw new ProcessCanceledException();
  }

  public void setCurrent(VcsDirtyScope scope) {
    myScope = scope;
    myFoldersCutDownWorker = new FoldersCutDownWorker();
  }

  @Override
  public void processChange(Change change, VcsKey vcsKey) {
    processChangeInList(change, (ChangeList)null, vcsKey);
  }

  @Override
  public void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    checkIfDisposed();

    LOG.debug("[processChangeInList-1] entering, cl name: " + ((changeList == null) ? null: changeList.getName()) +
              " change: " + ChangesUtil.getFilePath(change).getPath());
    final String fileName = ChangesUtil.getFilePath(change).getName();
    if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
      LOG.debug("[processChangeInList-1] file type ignored");
      return;
    }

    if (ChangeListManagerImpl.isUnder(change, myScope)) {
      if (changeList != null) {
        LOG.debug("[processChangeInList-1] to add change to cl");
        myChangeListUpdater.addChangeToList(changeList.getName(), change, vcsKey);
      }
      else {
        LOG.debug("[processChangeInList-1] to add to corresponding list");
        myChangeListUpdater.addChangeToCorrespondingList(change, vcsKey);
      }
    }
    else {
      LOG.debug("[processChangeInList-1] not under scope");
    }
  }

  @Override
  public void processChangeInList(Change change, String changeListName, VcsKey vcsKey) {
    checkIfDisposed();

    LocalChangeList list = null;
    if (changeListName != null) {
      list = myChangeListUpdater.findOrCreateList(changeListName, null);
    }
    processChangeInList(change, list, vcsKey);
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
    myChangeListUpdater.removeRegisteredChangeFor(path);
  }

  @Override
  public void processUnversionedFile(VirtualFile file) {
    if (acceptFile(file, false)) {
      if (myChangeListManager.isIgnoredFile(file)) {
        myComposite.getIgnoredFileHolder().addFile(file);
      }
      else if (myComposite.getIgnoredFileHolder().containsFile(file)) {
        // does not need to add: parent dir is already added
      }
      else {
        myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).addFile(file);
      }
      // if a file was previously marked as switched through recursion, remove it from switched list
      myComposite.getSwitchedFileHolder().removeFile(file);
    }
  }

  @Override
  public void processLocallyDeletedFile(FilePath file) {
    processLocallyDeletedFile(new LocallyDeletedChange(file));
  }

  @Override
  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
    checkIfDisposed();

    final FilePath file = locallyDeletedChange.getPath();
    if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;

    if (myScope.belongsTo(file)) {
      myComposite.getDeletedFileHolder().addFile(locallyDeletedChange);
    }
  }

  @Override
  public void processModifiedWithoutCheckout(VirtualFile file) {
    if (acceptFile(file, false)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("processModifiedWithoutCheckout " + file);
      }
      myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).addFile(file);
    }
  }

  @Override
  public void processIgnoredFile(VirtualFile file) {
    if (acceptFile(file, false)) {
      myComposite.getIgnoredFileHolder().addFile(myScope.getVcs(), file);
    }
  }

  @Override
  public void processLockedFolder(VirtualFile file) {
    if (acceptFile(file, true)) {
      if (myFoldersCutDownWorker.addCurrent(file)) {
        myComposite.getVFHolder(FileHolder.HolderType.LOCKED).addFile(file);
      }
    }
  }

  @Override
  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
    if (acceptFile(file, true)) {
      myComposite.getLogicallyLockedFileHolder().add(file, logicalLock);
    }
  }

  @Override
  public void processSwitchedFile(VirtualFile file, String branch, boolean recursive) {
    if (acceptFile(file, false)) {
      myComposite.getSwitchedFileHolder().addFile(file, branch, recursive);
    }
  }

  @Override
  public void processRootSwitch(VirtualFile file, String branch) {
    if (acceptFile(file, true)) {
      myComposite.getRootSwitchFileHolder().addFile(file, branch, false);
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

  private boolean acceptFile(@Nullable VirtualFile file, boolean allowIgnored) {
    checkIfDisposed();
    if (file == null) return false;
    if (!allowIgnored && ReadAction.compute(() -> myVcsManager.isIgnored(file))) return false;
    return myScope.belongsTo(VcsUtil.getFilePath(file));
  }
}
