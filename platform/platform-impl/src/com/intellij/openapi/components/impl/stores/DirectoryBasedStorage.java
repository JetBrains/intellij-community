/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;

//todo: support missing plugins
//todo: support storage data
public class DirectoryBasedStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DirectoryBasedStorage");
  private static final IFile[] EMPTY_FILES = new IFile[0];

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final IFile myDir;
  private final StateSplitter mySplitter;
  private final FileTypeManager myFileTypeManager;

  private Object mySession;
  private DirectoryStorageData myStorageData = null;

  public DirectoryBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                               @NotNull String dir,
                               @NotNull StateSplitter splitter,
                               @NotNull Disposable parentDisposable,
                               @NotNull PicoContainer picoContainer) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = FILE_SYSTEM.createFile(dir);
    mySplitter = splitter;
    Disposer.register(parentDisposable, this);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);

    if (virtualFileTracker != null && messageBus != null) {
      final String path = myDir.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL_PREFIX + path.replace(File.separatorChar, '/');
      final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        @Override
        public void contentsChanged(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        @Override
        public void fileDeleted(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        @Override
        public void fileCreated(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }
      }, false, this);
    }

    myFileTypeManager = FileTypeManager.getInstance();
  }

  @Override
  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    if (myStorageData == null) myStorageData = loadState();


    if (!myStorageData.containsComponent(componentName)) {
      return DefaultStateSerializer.deserializeState(new Element(StorageData.COMPONENT), stateClass, mergeInto);
    }

    return myStorageData.getMergedState(componentName, stateClass, mySplitter, mergeInto);
  }

  private DirectoryStorageData loadState() throws StateStorageException {
    DirectoryStorageData storageData = new DirectoryStorageData();
    storageData.loadFrom(LocalFileSystem.getInstance().findFileByIoFile(myDir), myPathMacroSubstitutor);
    return storageData;
  }


  @Override
  public boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException {
    if (!myDir.exists()) return false;
    if (reloadData) myStorageData = null;
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
    final ExternalizationSession session = new MyExternalizationSession(myStorageData.clone());

    mySession = session;
    return session;
  }

  @Override
  @NotNull
  public SaveSession startSave(@NotNull final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final MySaveSession session =
      new MySaveSession(((MyExternalizationSession)externalizationSession).myStorageData, myPathMacroSubstitutor);
    mySession = session;
    return session;
  }

  @Override
  public void finishSave(@NotNull final SaveSession saveSession) {
    try {
      LOG.assertTrue(mySession == saveSession);
    } finally {
      mySession = null;
    }
  }

  @Override
  public void reload(@NotNull final Set<String> changedComponents) throws StateStorageException {
    myStorageData = loadState();
  }

  @Override
  public void dispose() {
  }

  private class MySaveSession implements SaveSession, SafeWriteRequestor {
    private final DirectoryStorageData myStorageData;
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

    private MySaveSession(final DirectoryStorageData storageData, final TrackingPathMacroSubstitutor pathMacroSubstitutor) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
    }

    @Override
    public void save() throws StateStorageException {
      assert mySession == this;
      final Set<String> currentNames = new HashSet<String>();

      IFile[] children = myDir.exists() ? myDir.listFiles() : EMPTY_FILES;
      for (IFile child : children) {
        final String fileName = child.getName();
        if (!myFileTypeManager.isFileIgnored(fileName) && StringUtil.endsWithIgnoreCase(fileName, ".xml")) {
          currentNames.add(fileName);
        }
      }

      myStorageData.process(new DirectoryStorageData.StorageDataProcessor() {
        @Override
        public void process(final String componentName, final IFile file, final Element element) {
          currentNames.remove(file.getName());

          if (myPathMacroSubstitutor != null) {
            myPathMacroSubstitutor.collapsePaths(element);
          }

          if (file.getTimeStamp() <= myStorageData.getLastTimeStamp()) {
            if (!myDir.exists()) {
              myDir.createParentDirs();
              myDir.mkDir();
            }

            StorageUtil.save(file, element, MySaveSession.this);
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
              IFile child = myDir.getChild(name);

              if (child.getTimeStamp() > myStorageData.getLastTimeStamp()) {
                // do not touch new files during VC update (which aren't read yet)
                // now got an opposite problem: file is recreated if was removed by VC during update.
                return;
              }

              final VirtualFile virtualFile = StorageUtil.getVirtualFile(child);
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

    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      boolean containsSelf = false;

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == DirectoryBasedStorage.this) {
          VirtualFile file = pair.first;
          if ("xml".equalsIgnoreCase(file.getExtension())) {
            containsSelf = true;
            break;
          }
        }
      }

      if (!containsSelf) return Collections.emptySet();

      if (myStorageData.getComponentNames().size() == 0) {
        // no state yet, so try to initialize it now
        final DirectoryStorageData storageData = loadState();
        return new HashSet<String>(storageData.getComponentNames());
      }

      return new HashSet<String>(myStorageData.getComponentNames());
    }

    @Override
    @NotNull
    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      assert mySession == this;

      if (!myDir.exists()) return getAllStorageFiles();
      assert myDir.isDirectory();

      final List<IFile> filesToSave = new ArrayList<IFile>();

      IFile[] children = myDir.listFiles();
      final Set<String> currentChildNames = new HashSet<String>();
      for (IFile child : children) {
        if (!myFileTypeManager.isFileIgnored(child.getName())) currentChildNames.add(child.getName());
      }

      myStorageData.process(new DirectoryStorageData.StorageDataProcessor() {
        @Override
        public void process(final String componentName, final IFile file, final Element element) {
          if (currentChildNames.contains(file.getName())) {
            currentChildNames.remove(file.getName());

            if (myPathMacroSubstitutor != null) {
              myPathMacroSubstitutor.collapsePaths(element);
            }

            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            if (virtualFile == null || !StorageUtil.contentEquals(element, virtualFile)) {
              filesToSave.add(file);
            }
          }

        }
      });

      for (String childName : currentChildNames) {
        final IFile child = myDir.getChild(childName);
        filesToSave.add(child);
      }

      return filesToSave;
    }

    @Override
    @NotNull
    public List<IFile> getAllStorageFiles() {
      return new ArrayList<IFile>(myStorageData.getAllStorageFiles().keySet());
    }
  }

  private class MyExternalizationSession implements ExternalizationSession {
    private final DirectoryStorageData myStorageData;

    private MyExternalizationSession(final DirectoryStorageData storageData) {
      myStorageData = storageData;
    }

    @Override
    public void setState(@NotNull final Object component, final String componentName, @NotNull final Object state, final Storage storageSpec)
      throws StateStorageException {
      assert mySession == this;
      setState(componentName, state, storageSpec);
    }

    private void setState(final String componentName, @NotNull Object state, final Storage storageSpec) throws StateStorageException {
      try {
        final Element element = DefaultStateSerializer.serializeState(state, storageSpec);
        for (Pair<Element, String> pair : mySplitter.splitState(element)) {
          Element e = pair.first;
          String name = pair.second;

          Element statePart = new Element(StorageData.COMPONENT);
          statePart.setAttribute(StorageData.NAME, componentName);
          statePart.addContent(e.detach());

          myStorageData.put(componentName, myDir.getChild(name), statePart, false);
        }
      }
      catch (WriteExternalException e) {
        throw new StateStorageException(e);
      }
    }
  }
}
