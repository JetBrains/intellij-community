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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.LineSeparator;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.Set;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance(FileBasedStorage.class);

  private final String myFilePath;
  private final File myFile;
  private volatile VirtualFile myCachedVirtualFile;
  private LineSeparator myLineSeparator;

  public FileBasedStorage(@NotNull String filePath,
                          @NotNull String fileSpec,
                          @Nullable RoamingType roamingType,
                          @Nullable TrackingPathMacroSubstitutor pathMacroManager,
                          @NotNull String rootElementName,
                          @NotNull Disposable parentDisposable,
                          @Nullable final Listener listener,
                          @Nullable StreamProvider streamProvider,
                          ComponentVersionProvider componentVersionProvider) {
    super(fileSpec, roamingType, pathMacroManager, parentDisposable, rootElementName, streamProvider, componentVersionProvider);

    myFilePath = filePath;
    myFile = new File(filePath);

    if (listener != null) {
      VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
      if (virtualFileTracker != null) {
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
              assert listener != null;
              listener.storageFileChanged(event, FileBasedStorage.this);
            }
          }
        }, false, this);
      }
    }
  }

  protected boolean isUseXmlProlog() {
    return false;
  }

  protected boolean isUseLfLineSeparatorByDefault() {
    return isUseXmlProlog();
  }

  @Override
  protected MySaveSession createSaveSession(@NotNull StorageData storageData) {
    return new FileSaveSession(storageData);
  }

  private class FileSaveSession extends MySaveSession {
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
        throw new StateStorageException("It seems like some macros were not expanded for path: " + myFile.getPath());
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

      if (content == null) {
        StorageUtil.deleteFile(myFile, this, getVirtualFile());
        myCachedVirtualFile = null;
      }
      else {
        VirtualFile file = getVirtualFile();
        if (file == null || !file.exists()) {
          FileUtil.createParentDirs(myFile);
          file = null;
        }
        myCachedVirtualFile = StorageUtil.writeFile(myFile, this, file, content, isUseXmlProlog() ? myLineSeparator : null);
      }
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
      return JDOMUtil.loadDocument(charBuffer).getRootElement();
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
    myBlockSavingTheContent = isProjectOrModuleOrWorkspaceFile() && !contentTruncated;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (e != null) {
        LOG.info(e);
      }
      Notifications.Bus.notify(
        new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Load Settings",
                         "Cannot load settings from file '" + myFile.getPath() + "': " + (e == null ? "content truncated" : e.getLocalizedMessage()) + "\n" +
                         getInvalidContentMessage(contentTruncated), NotificationType.WARNING));
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

  public void updatedFromStreamProvider(@NotNull Set<String> changedComponentNames, boolean deleted) {
    resetProviderCache();

    try {
      Element newElement = deleted ? null : loadDataFromStreamProvider();
      if (newElement == null) {
        StorageUtil.deleteFile(myFile, this, myCachedVirtualFile);
        // if data was loaded, mark as changed all loaded components
        if (myLoadedData != null) {
          changedComponentNames.addAll(myLoadedData.getComponentNames());
          myLoadedData = null;
        }
      }
      else if (myLoadedData != null) {
        StorageData newStorageData = createStorageData();
        loadState(newStorageData, newElement);
        changedComponentNames.addAll(myLoadedData.getChangedComponentNames(newStorageData, myPathMacroSubstitutor));
        myLoadedData = newStorageData;
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @Nullable
  @Deprecated
  public File updateFileExternallyFromStreamProviders() throws IOException {
    Element element = getElement(loadData(true), true, Collections.<String, Element>emptyMap());
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
