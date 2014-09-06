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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance(FileBasedStorage.class);

  private static boolean ourConfigDirectoryRefreshed = false;

  private final String myFilePath;
  private final File myFile;
  private volatile VirtualFile myCachedVirtualFile;

  public FileBasedStorage(@NotNull String filePath,
                          @NotNull String fileSpec,
                          @Nullable RoamingType roamingType,
                          @Nullable TrackingPathMacroSubstitutor pathMacroManager,
                          @NotNull String rootElementName,
                          @NotNull Disposable parentDisposable,
                          PicoContainer picoContainer,
                          @Nullable StreamProvider streamProvider,
                          ComponentVersionProvider componentVersionProvider) {
    super(fileSpec, roamingType, pathMacroManager, parentDisposable, rootElementName, streamProvider, componentVersionProvider);

    refreshConfigDirectoryOnce();

    myFilePath = filePath;
    myFile = new File(filePath);

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);
    if (virtualFileTracker != null && messageBus != null) {
      final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
      virtualFileTracker.addTracker(LocalFileSystem.PROTOCOL_PREFIX + myFile.getAbsolutePath().replace(File.separatorChar, '/'), new VirtualFileAdapter() {
        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
          myCachedVirtualFile = null;
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          myCachedVirtualFile = null;
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
          myCachedVirtualFile = event.getFile();
        }

        @Override
        public void contentsChanged(@NotNull final VirtualFileEvent event) {
          if (!isDisposed()) {
            listener.storageFileChanged(event, FileBasedStorage.this);
          }
        }
      }, false, this);
    }
  }

  private static void refreshConfigDirectoryOnce() {
    Application app = ApplicationManager.getApplication();
    if (!ourConfigDirectoryRefreshed && (app.isUnitTestMode() || app.isDispatchThread())) {
      try {
        VirtualFile configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManager.getConfigPath());
        if (configDir != null) {
          VfsUtilCore.visitChildrenRecursively(configDir, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
              return !"componentVersions".equals(file.getName());
            }
          });
          VfsUtil.markDirtyAndRefresh(false, true, false, configDir);
        }
      }
      finally {
        ourConfigDirectoryRefreshed = true;
      }
    }
  }

  @Override
  protected MySaveSession createSaveSession(final MyExternalizationSession externalizationSession) {
    return new FileSaveSession(externalizationSession);
  }

  private class FileSaveSession extends MySaveSession {
    protected FileSaveSession(MyExternalizationSession externalizationSession) {
      super(externalizationSession);
    }

    @Override
    protected boolean physicalContentNeedsSave() {
      VirtualFile file = getVirtualFile();
      if (file == null || !file.exists()) {
        return !myStorageData.isEmpty();
      }
      Element element = getElementToSave();
      return element == null || !StorageUtil.contentEquals(element, file);
    }

    @Override
    protected int calcHash() {
      int hash = myStorageData.getHash();
      if (myPathMacroSubstitutor != null) {
        hash = 31 * hash + myPathMacroSubstitutor.hashCode();
      }
      return hash;
    }

    @Override
    protected void doSave() throws StateStorageException {
      if (myBlockSavingTheContent) {
        return;
      }
      if (ApplicationManager.getApplication().isUnitTestMode() && myFile != null && StringUtil.startsWithChar(myFile.getPath(), '$')) {
        throw new StateStorageException("It seems like some macros were not expanded for path: " + myFile.getPath());
      }

      LOG.assertTrue(myFile != null);
      myCachedVirtualFile = StorageUtil.save(myFile, getElementToSave(), this, true, myCachedVirtualFile);
    }

    @NotNull
    @Override
    public Collection<File> getStorageFilesToSave() {
      if (needsSave()) {
        if (LOG.isDebugEnabled()) {
          LOG.info("File " + myFileSpec + " needs save; hash=" + myUpToDateHash + "; currentHash=" + calcHash() + "; " +
                   "content needs save=" + physicalContentNeedsSave());
        }
        return getAllStorageFiles();
      }
      else {
        return Collections.emptyList();
      }
    }

    @NotNull
    @Override
    public List<File> getAllStorageFiles() {
      return Collections.singletonList(myFile);
    }
  }

  @Override
  @NotNull
  protected StorageData createStorageData() {
    FileStorageData data = new FileStorageData(myRootElementName);
    data.myFilePath = myFilePath;
    return data;
  }

  public static class FileStorageData extends StorageData {
    String myFilePath;

    public FileStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected FileStorageData(FileStorageData storageData) {
      super(storageData);
      myFilePath = storageData.myFilePath;
    }

    @Override
    public StorageData clone() {
      return new FileStorageData(this);
    }

    @NonNls
    public String toString() {
      return "FileStorageData[" + myFilePath + "]";
    }
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    VirtualFile virtualFile = myCachedVirtualFile;
    if (virtualFile == null) {
      myCachedVirtualFile = virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myFile);
    }
    return virtualFile;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @NotNull
  public String getFilePath() {
    return myFilePath;
  }

  @Override
  @Nullable
  protected Element loadLocalData() {
    myBlockSavingTheContent = false;
    try {
      VirtualFile file = getVirtualFile();
      if (file == null || file.isDirectory() || !file.isValid()) {
        LOG.info("Document was not loaded for " + myFileSpec + " file is " + (file == null ? "null" : "directory"));
        return null;
      }
      if (file.getLength() == 0) {
        return processReadException(null);
      }
      return StorageData.load(file);
    }
    catch (final JDOMException e) {
      return processReadException(e);
    }
    catch (final IOException e) {
      return processReadException(e);
    }
  }

  @Nullable
  private Element processReadException(@Nullable final Exception e) {
    boolean contentTruncated = e == null;
    myBlockSavingTheContent = isProjectOrModuleOrWorkspaceFile() && !contentTruncated;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (e != null) {
        LOG.info(e);
      }
      final String message = "Cannot load settings from file '" + myFile.getPath() + "': " + (e == null ? "content truncated" : e.getLocalizedMessage()) + "\n" +
                             getInvalidContentMessage(contentTruncated);
      Notifications.Bus.notify(
        new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Load Settings", message, NotificationType.WARNING));
    }

    return null;
  }

  private boolean isProjectOrModuleOrWorkspaceFile() {
    return StorageUtil.isProjectOrModuleFile(myFileSpec) || myFileSpec.equals(StoragePathMacros.WORKSPACE_FILE);
  }

  private String getInvalidContentMessage(boolean contentTruncated) {
    return isProjectOrModuleOrWorkspaceFile() && !contentTruncated ? "Please correct the file content" : "File content will be recreated";
  }

  @Override
  public void setDefaultState(final Element element) {
    element.setName(myRootElementName);
    super.setDefaultState(element);
  }

  @Nullable
  public File updateFileExternallyFromStreamProviders() throws IOException {
    Element element = getElement(loadData(true));
    if (element == null) {
      FileUtil.delete(myFile);
      return null;
    }

    BufferExposingByteArrayOutputStream out = StorageUtil.newContentIfDiffers(element, getVirtualFile());
    if (out == null) {
      return null;
    }

    File file = new File(myFile.getAbsolutePath());
    FileUtil.writeToFile(file, out.getInternalBuffer(), 0, out.size());
    return file;
  }
}
