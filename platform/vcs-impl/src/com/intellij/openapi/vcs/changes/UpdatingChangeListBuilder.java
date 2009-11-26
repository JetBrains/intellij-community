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
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

class UpdatingChangeListBuilder implements ChangelistBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdatingChangeListBuilder");
  private final ChangeListWorker myChangeListWorker;
  private final FileHolderComposite myComposite;
  // todo +-
  private final Getter<Boolean> myDisposedGetter;
  private VcsDirtyScope myScope;
  private FoldersCutDownWorker myFoldersCutDownWorker;
  private final boolean myUpdateUnversioned;
  private final IgnoredFilesComponent myIgnoredFilesComponent;
  private final ExcludedFileIndex myIndex;
  private final ChangeListManagerGate myGate;

  UpdatingChangeListBuilder(final ChangeListWorker changeListWorker,
                            final FileHolderComposite composite,
                            final Getter<Boolean> disposedGetter,
                            final boolean updateUnversioned,
                            final IgnoredFilesComponent ignoredFilesComponent, final ChangeListManagerGate gate) {
    myChangeListWorker = changeListWorker;
    myComposite = composite;
    myDisposedGetter = disposedGetter;
    myUpdateUnversioned = updateUnversioned;
    myIgnoredFilesComponent = ignoredFilesComponent;
    myGate = gate;
    myIndex = ExcludedFileIndex.getInstance(changeListWorker.getProject());
  }

  private void checkIfDisposed() {
    if (myDisposedGetter.get()) throw new ChangeListManagerImpl.DisposedException();
  }

  public void setCurrent(final VcsDirtyScope scope, final FoldersCutDownWorker foldersWorker) {
    myScope = scope;
    myFoldersCutDownWorker = foldersWorker;
  }

  public void processChange(final Change change, VcsKey vcsKey) {
    processChangeInList( change, (ChangeList) null, vcsKey);
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
          } else {
            LOG.debug("[processChangeInList-1] to add to corresponding list");
            myChangeListWorker.addChangeToCorrespondingList(change, vcsKey);
          }
        } else {
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

  private boolean isExcluded(final VirtualFile file) {
    return myIndex.isExcludedFile(file);
  }

  public void processUnversionedFile(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (isExcluded(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      if (myIgnoredFilesComponent.isIgnoredFile(file)) {
        myComposite.getIgnoredFileHolder().addFile(file, "", false);
      } else if (myComposite.getIgnoredFileHolder().containsFile(file)) {
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
    if (! myUpdateUnversioned) return;
    checkIfDisposed();
    final FilePath file = locallyDeletedChange.getPath();
    if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
    if (myScope.belongsTo(file)) {
      myChangeListWorker.addLocallyDeleted(locallyDeletedChange);
    }
  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (isExcluded(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).addFile(file);
    }
  }

  public void processIgnoredFile(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (isExcluded(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myComposite.getIgnoredFileHolder().addFile(file, "", false);
    }
  }

  public void processLockedFolder(final VirtualFile file) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(new FilePathImpl(file))) {
      if (myFoldersCutDownWorker.addCurrent(file)) {
        myComposite.getVFHolder(FileHolder.HolderType.LOCKED).addFile(file);
      }
    }
  }

  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(new FilePathImpl(file))) {
      ((LogicallyLockedHolder) myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).add(file, logicalLock);
    }
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (isExcluded(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myChangeListWorker.addSwitched(file, branch, recursive);
    }
  }

  public boolean isUpdatingUnversionedFiles() {
    return myUpdateUnversioned;
  }

  public boolean reportChangesOutsideProject() {
    return false;
  }
}
