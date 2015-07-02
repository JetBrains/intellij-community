/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.LineSeparator;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Set;

public class FileBasedStorage extends XmlElementStorage {
  private final File myFile;
  private volatile VirtualFile myCachedVirtualFile;
  private LineSeparator myLineSeparator;

  public FileBasedStorage(@NotNull File file,
                          @NotNull String fileSpec,
                          @Nullable RoamingType roamingType,
                          @Nullable TrackingPathMacroSubstitutor pathMacroManager,
                          @NotNull String rootElementName,
                          @NotNull Disposable parentDisposable,
                          @Nullable final Listener listener,
                          @Nullable StreamProvider streamProvider) {
    super(fileSpec, roamingType, pathMacroManager, rootElementName, streamProvider);

    myFile = file;

    if (listener != null) {
      VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
      if (virtualFileTracker != null) {
        virtualFileTracker.addTracker(VfsUtilCore.pathToUrl(getFilePath()), new VirtualFileAdapter() {
          @Override
          public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            myCachedVirtualFile = null;
          }

          @Override
          public void fileDeleted(@NotNull VirtualFileEvent event) {
            myCachedVirtualFile = null;
            listener.storageFileChanged(event, FileBasedStorage.this);
          }

          @Override
          public void fileCreated(@NotNull VirtualFileEvent event) {
            myCachedVirtualFile = event.getFile();
            listener.storageFileChanged(event, FileBasedStorage.this);
          }

          @Override
          public void contentsChanged(@NotNull final VirtualFileEvent event) {
            listener.storageFileChanged(event, FileBasedStorage.this);
          }
        }, false, parentDisposable);
      }
    }
  }

  protected boolean isUseXmlProlog() {
    return false;
  }

  protected boolean isUseLfLineSeparatorByDefault() {
    return isUseXmlProlog();
  }

  @NotNull
  @Override
  protected XmlElementStorageSaveSession createSaveSession(@NotNull StorageData storageData) {
    return new FileSaveSession(storageData);
  }

  private class FileSaveSession extends XmlElementStorageSaveSession {
    protected FileSaveSession(@NotNull StorageData storageData) {
      super(storageData);
    }

    @Override
    protected void doSave(@Nullable Element element) throws IOException {
      if (myLineSeparator == null) {
        myLineSeparator = isUseLfLineSeparatorByDefault() ? LineSeparator.LF : LineSeparator.getSystemLineSeparator();
      }

      BufferExposingByteArrayOutputStream content = element == null ? null : StorageUtil.writeToBytes(element, myLineSeparator.getSeparatorString());
      if (ApplicationManager.getApplication().isUnitTestMode() && StringUtil.startsWithChar(myFile.getPath(), '$')) {
        throw new IOException("It seems like some macros were not expanded for path: " + myFile);
      }

      try {
        if (myStreamProvider != null && myStreamProvider.isEnabled()) {
          // stream provider always use LF separator
          saveForProvider(myLineSeparator == LineSeparator.LF ? content : null, element);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }

      if (LOG.isDebugEnabled() && myFileSpec.equals(StoragePathMacros.MODULE_FILE)) {
        LOG.debug("doSave " + getFilePath());
      }

      VirtualFile virtualFile = getVirtualFile();
      if (content == null) {
        StorageUtil.deleteFile(myFile, this, virtualFile);
        myCachedVirtualFile = null;
      }
      else {
        myCachedVirtualFile = StorageUtil.writeFile(myFile, this, virtualFile, content, isUseXmlProlog() ? myLineSeparator : null);
      }
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
    return PathUtil.toSystemIndependentName(myFile.getPath());
  }

  @Override
  @Nullable
  protected Element loadLocalData() {
    myBlockSavingTheContent = false;
    try {
      VirtualFile file = getVirtualFile();
      if (file == null || file.isDirectory() || !file.isValid()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Document was not loaded for " + myFileSpec + " file is " + (file == null ? "null" : "directory"));
        }
        return null;
      }
      if (file.getLength() == 0) {
        return processReadException(null);
      }

      CharBuffer charBuffer = CharsetToolkit.UTF8_CHARSET.decode(ByteBuffer.wrap(file.contentsToByteArray()));
      myLineSeparator = StorageUtil.detectLineSeparators(charBuffer, isUseLfLineSeparatorByDefault() ? null : LineSeparator.LF);
      return JDOMUtil.loadDocument(charBuffer).detachRootElement();
    }
    catch (JDOMException e) {
      return processReadException(e);
    }
    catch (IOException e) {
      return processReadException(e);
    }
  }

  @Nullable
  private Element processReadException(@Nullable Exception e) {
    boolean contentTruncated = e == null;
    myBlockSavingTheContent = !contentTruncated && (StorageUtil.isProjectOrModuleFile(myFileSpec) || myFileSpec.equals(StoragePathMacros.WORKSPACE_FILE));
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (e != null) {
        LOG.info(e);
      }
      new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Load Settings",
                       "Cannot load settings from file '" +
                       myFile + "': " +
                       (e == null ? "content truncated" : e.getMessage()) + "\n" +
                       (myBlockSavingTheContent ? "Please correct the file content" : "File content will be recreated"),
                       NotificationType.WARNING).notify(null);
    }
    return null;
  }

  @Override
  public void setDefaultState(@NotNull Element element) {
    element.setName(myRootElementName);
    super.setDefaultState(element);
  }

  @SuppressWarnings("unused")
  public void updatedFromStreamProvider(@NotNull Set<String> changedComponentNames, boolean deleted) {
    if (myRoamingType == RoamingType.DISABLED) {
      // storage roaming was changed to DISABLED, but settings repository has old state
      return;
    }

    try {
      Element newElement = deleted ? null : loadDataFromStreamProvider();
      if (newElement == null) {
        StorageUtil.deleteFile(myFile, this, myCachedVirtualFile);
        // if data was loaded, mark as changed all loaded components
        if (myStorageData != null) {
          changedComponentNames.addAll(myStorageData.getComponentNames());
          myStorageData = null;
        }
      }
      else if (myStorageData != null) {
        StorageData newStorageData = createStorageData();
        loadState(newStorageData, newElement);
        changedComponentNames.addAll(myStorageData.getChangedComponentNames(newStorageData, myPathMacroSubstitutor));
        myStorageData = newStorageData;
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @Override
  public String toString() {
    return getFilePath();
  }
}
