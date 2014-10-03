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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

//todo: support missing plugins
//todo: support storage data
public class DirectoryBasedStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DirectoryBasedStorage");

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final File myDir;
  private final StateSplitter mySplitter;
  private final FileTypeManager myFileTypeManager;

  private DirectoryStorageData myStorageData = null;

  public DirectoryBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                               @NotNull String dir,
                               @NotNull StateSplitter splitter,
                               @NotNull Disposable parentDisposable,
                               @Nullable final Listener listener) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = new File(dir);
    mySplitter = splitter;
    Disposer.register(parentDisposable, this);

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null && listener != null) {
      final String path = myDir.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL_PREFIX + path.replace(File.separatorChar, '/');
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        @Override
        public void contentsChanged(@NotNull final VirtualFileEvent event) {
          if (!StringUtilRt.endsWithIgnoreCase(event.getFile().getNameSequence(), ".xml")) return;
          assert listener != null;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        @Override
        public void fileDeleted(@NotNull final VirtualFileEvent event) {
          if (!StringUtilRt.endsWithIgnoreCase(event.getFile().getNameSequence(), ".xml")) return;
          assert listener != null;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        @Override
        public void fileCreated(@NotNull final VirtualFileEvent event) {
          if (!StringUtilRt.endsWithIgnoreCase(event.getFile().getNameSequence(), ".xml")) return;
          assert listener != null;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }
      }, false, this);
    }

    myFileTypeManager = FileTypeManager.getInstance();
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull Set<String> result) {
    boolean containsSelf = false;
    for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
      if (pair.second == this && StringUtilRt.endsWithIgnoreCase(pair.first.getNameSequence(), ".xml")) {
        containsSelf = true;
        break;
      }
    }

    if (!containsSelf) {
      return;
    }

    // todo reload only changed file, compute diff
    myStorageData = loadState();
    result.addAll(myStorageData.getComponentNames());
  }

  @Override
  @Nullable
  public <T> T getState(final Object component, @NotNull final String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto) {
    if (myStorageData == null) {
      myStorageData = loadState();
    }

    if (!myStorageData.containsComponent(componentName)) {
      return DefaultStateSerializer.deserializeState(new Element(StorageData.COMPONENT), stateClass, mergeInto);
    }

    return myStorageData.getMergedState(componentName, stateClass, mySplitter, mergeInto);
  }

  private DirectoryStorageData loadState() {
    DirectoryStorageData storageData = new DirectoryStorageData();
    storageData.loadFrom(LocalFileSystem.getInstance().findFileByIoFile(myDir), myPathMacroSubstitutor);
    return storageData;
  }

  @Override
  public boolean hasState(@Nullable final Object component, @NotNull String componentName, final Class<?> aClass, final boolean reloadData) {
    if (!myDir.exists()) {
      return false;
    }
    if (reloadData) {
      myStorageData = null;
    }
    return true;
  }

  @Override
  @NotNull
  public ExternalizationSession startExternalization() {
    if (myStorageData == null) {
      try {
        myStorageData = loadState();
      }
      catch (StateStorageException e) {
        LOG.error(e);
      }
    }
    return new MyExternalizationSession(myStorageData.clone());
  }

  @Nullable
  @Override
  public SaveSession startSave(@NotNull ExternalizationSession externalizationSession) {
    return new MySaveSession(((MyExternalizationSession)externalizationSession).myStorageData, myPathMacroSubstitutor);
  }

  @Override
  public void dispose() {
  }

  private class MySaveSession implements SaveSession {
    private final DirectoryStorageData myStorageData;
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

    private MySaveSession(@NotNull DirectoryStorageData storageData, @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
    }

    @Override
    public void save() throws StateStorageException {
      final Set<String> currentNames = new SmartHashSet<String>();
      File[] children = myDir.listFiles();
      if (children != null) {
        for (File child : children) {
          final String fileName = child.getName();
          if (!myFileTypeManager.isFileIgnored(fileName) && StringUtil.endsWithIgnoreCase(fileName, ".xml")) {
            currentNames.add(fileName);
          }
        }
      }

      myStorageData.process(new DirectoryStorageData.StorageDataProcessor() {
        @Override
        public void process(final String componentName, final File file, final Element element) {
          currentNames.remove(file.getName());

          if (myPathMacroSubstitutor != null) {
            myPathMacroSubstitutor.collapsePaths(element);
          }

          if (file.lastModified() <= myStorageData.getLastTimeStamp()) {
            StorageUtil.save(file, element, MySaveSession.this, false, LocalFileSystem.getInstance().findFileByIoFile(file));
            myStorageData.updateLastTimestamp(file);
          }
        }
      });

      if (myDir.exists() && !currentNames.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          if (myDir.exists()) {
            for (String name : currentNames) {
              File child = new File(myDir, name);
              if (child.lastModified() > myStorageData.getLastTimeStamp()) {
                // do not touch new files during VC update (which aren't read yet)
                // now got an opposite problem: file is recreated if was removed by VC during update.
                return;
              }

              final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(child);
              if (virtualFile != null) {
                try {
                  LOG.debug("Removing configuration file: " + virtualFile.getPresentableUrl());
                  virtualFile.delete(MySaveSession.this);
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }
      });
      }

      myStorageData.clear();
    }
  }

  private class MyExternalizationSession implements ExternalizationSession {
    private final DirectoryStorageData myStorageData;

    private MyExternalizationSession(final DirectoryStorageData storageData) {
      myStorageData = storageData;
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
        for (Pair<Element, String> pair : mySplitter.splitState(element)) {
          Element e = pair.first;
          String name = pair.second;

          Element statePart = new Element(StorageData.COMPONENT);
          statePart.setAttribute(StorageData.NAME, componentName);
          statePart.addContent(e.detach());

          myStorageData.put(componentName, new File(myDir, name), statePart, false);
        }
      }
    }
  }
}
