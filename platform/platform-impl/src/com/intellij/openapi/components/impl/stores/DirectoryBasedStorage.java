/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.store.StateStorageBase;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

//todo: support missing plugins
//todo: support storage data
public class DirectoryBasedStorage extends StateStorageBase {
  private final File myDir;
  private volatile VirtualFile myVirtualFile;
  private final StateSplitter mySplitter;

  private DirectoryStorageData myStorageData;

  public DirectoryBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                               @NotNull String dir,
                               @NotNull StateSplitter splitter,
                               @NotNull Disposable parentDisposable,
                               @Nullable final Listener listener) {
    super(pathMacroSubstitutor);

    myDir = new File(dir);
    mySplitter = splitter;

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null && listener != null) {
      virtualFileTracker.addTracker(LocalFileSystem.PROTOCOL_PREFIX + myDir.getAbsolutePath().replace(File.separatorChar, '/'), new VirtualFileAdapter() {
        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
          notifyIfNeed(event);
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          if (event.getFile().equals(myVirtualFile)) {
            myVirtualFile = null;
          }
          notifyIfNeed(event);
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
          notifyIfNeed(event);
        }

        private void notifyIfNeed(@NotNull VirtualFileEvent event) {
          // storage directory will be removed if the only child was removed
          if (event.getFile().isDirectory() || DirectoryStorageData.isStorageFile(event.getFile())) {
            listener.storageFileChanged(event, DirectoryBasedStorage.this);
          }
        }
      }, false, parentDisposable);
    }
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<VirtualFile> changedFiles, @NotNull Set<String> result) {
    // todo reload only changed file, compute diff
    DirectoryStorageData oldData = myStorageData;
    DirectoryStorageData newData = loadState();
    myStorageData = newData;
    if (oldData == null) {
      result.addAll(newData.getComponentNames());
    }
    else {
      result.addAll(oldData.getComponentNames());
      result.addAll(newData.getComponentNames());
    }
  }

  @Override
  @Nullable
  public <T> T getState(@Nullable Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto) {
    DirectoryStorageData storage = getStorageData(false);
    if (!storage.hasState(componentName)) {
      return DefaultStateSerializer.deserializeState(new Element(StorageData.COMPONENT), stateClass, mergeInto);
    }
    return storage.getMergedState(componentName, stateClass, mySplitter, mergeInto);
  }

  @NotNull
  private DirectoryStorageData loadState() {
    DirectoryStorageData storageData = new DirectoryStorageData();
    storageData.loadFrom(getVirtualFile(), myPathMacroSubstitutor);
    return storageData;
  }

  @Nullable
  private VirtualFile getVirtualFile() {
    VirtualFile virtualFile = myVirtualFile;
    if (virtualFile == null) {
      myVirtualFile = virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myDir);
    }
    return virtualFile;
  }

  @Override
  @NotNull
  protected DirectoryStorageData getStorageData(boolean reloadData) {
    if (myStorageData != null && !reloadData) {
      return myStorageData;
    }

    myStorageData = loadState();
    return myStorageData;
  }

  @Override
  @Nullable
  public ExternalizationSession startExternalization() {
    return checkIsSavingDisabled() ? null : new MySaveSession(this);
  }

  @Nullable
  @Override
  public SaveSession startSave(@NotNull ExternalizationSession externalizationSession) {
    return checkIsSavingDisabled() ? null : (MySaveSession)externalizationSession;
  }

  private static class MySaveSession implements SaveSession, ExternalizationSession {
    private final DirectoryBasedStorage myStorage;
    private final DirectoryStorageData myStorageData;

    private MySaveSession(@NotNull DirectoryBasedStorage storage) {
      myStorage = storage;
      myStorageData = storage.myStorageData == null ? storage.loadState() : storage.myStorageData.clone();
    }

    @Override
    public void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, Storage storageSpec) {
      Element element;
      try {
        element = DefaultStateSerializer.serializeState(state, storageSpec);
      }
      catch (WriteExternalException e) {
        throw new StateStorageException(e);
      }
      catch (Throwable e) {
        LOG.info("Unable to serialize component state!", e);
        return;
      }

      if (element != null) {
        for (Pair<Element, String> pair : myStorage.mySplitter.splitState(element)) {
          Element e = pair.first;
          String name = pair.second;

          Element statePart = new Element(StorageData.COMPONENT);
          statePart.setAttribute(StorageData.NAME, componentName);
          statePart.addContent(e.detach());

          myStorageData.put(componentName, new File(myStorage.myDir, name), statePart, false);
        }
      }
    }

    @Override
    public void save() {
      final VirtualFile dir = myStorage.getVirtualFile();
      final Set<String> existingFileNames = new SmartHashSet<String>();
      for (String componentName : myStorageData.getComponentNames()) {
        myStorageData.processComponent(componentName, new TObjectObjectProcedure<File, Element>() {
          @Override
          public boolean execute(File file, Element element) {
            String fileName = file.getName();
            existingFileNames.add(fileName);

            if (myStorage.myPathMacroSubstitutor != null) {
              myStorage.myPathMacroSubstitutor.collapsePaths(element);
            }

            if (file.lastModified() <= myStorageData.getLastTimeStamp()) {
              StorageUtil.save(file, element, MySaveSession.this, false, dir == null ? null : dir.findChild(fileName));
              myStorageData.updateLastTimestamp(file);
            }

            return true;
          }
        });
      }

      if (dir != null && dir.exists()) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        AccessToken token = WriteAction.start();
        try {
          for (VirtualFile file : dir.getChildren()) {
            String fileName = file.getName();
            if (fileTypeManager.isFileIgnored(fileName) || !DirectoryStorageData.isStorageFile(file) || existingFileNames.contains(fileName)) {
              continue;
            }

            if (file.getTimeStamp() > myStorageData.getLastTimeStamp()) {
              // do not touch new files during VC update (which aren't read yet)
              // now got an opposite problem: file is recreated if was removed by VC during update.
              return;
            }

            try {
              LOG.debug("Removing configuration file: " + file.getPresentableUrl());
              file.delete(this);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        }
        finally {
          token.finish();
        }
      }

      myStorage.myStorageData = myStorageData;
    }
  }
}
