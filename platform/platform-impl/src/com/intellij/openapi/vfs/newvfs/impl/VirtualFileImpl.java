/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LargeFileWriteRequestor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

public class VirtualFileImpl extends VirtualFileSystemEntry {
  VirtualFileImpl(int id, VfsData.Segment segment, VirtualDirectoryImpl parent) {
    super(id, segment, parent);
  }

  @Override
  @Nullable
  public NewVirtualFile findChild(@NotNull @NonNls final String name) {
    return null;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getCachedChildren() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Iterable<VirtualFile> iterInDbChildren() {
    return ContainerUtil.emptyIterable();
  }

  @Override
  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    final VirtualFileSystemEntry parent = getParent();
    assert parent != null;
    return parent.getFileSystem();
  }

  @Override
  @Nullable
  public NewVirtualFile refreshAndFindChild(@NotNull final String name) {
    return null;
  }

  @Override
  @Nullable
  public NewVirtualFile findChildIfCached(@NotNull final String name) {
    return null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  private static final Key<byte[]> ourPreloadedContentKey = Key.create("preloaded.content.key");

  @Override
  public void setPreloadedContentHint(byte[] preloadedContentHint) {
    putUserData(ourPreloadedContentKey, preloadedContentHint);
  }

  @Override
  @NotNull
  public InputStream getInputStream() throws IOException {
    final byte[] preloadedContent = getUserData(ourPreloadedContentKey);

    return VfsUtilCore.inputStreamSkippingBOM(
      preloadedContent == null ?
        ourPersistence.getInputStream(this):
        new DataInputStream(new UnsyncByteArrayInputStream(preloadedContent)),
      this
    );
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return contentsToByteArray(true);
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(boolean cacheContent) throws IOException {
    checkNotTooLarge(null);
    final byte[] preloadedContent = getUserData(ourPreloadedContentKey);
    if (preloadedContent != null) return preloadedContent;
    byte[] bytes = ourPersistence.contentsToByteArray(this, cacheContent);
    if (!isCharsetSet()) {
      // optimisation: take the opportunity to not load bytes again in getCharset()
      // use getByFile() to not fall into recursive trap from vfile.getFileType() which would try to load contents again to detect charset
      FileType fileType = ObjectUtils.notNull(((FileTypeManagerImpl)FileTypeManager.getInstance()).getByFile(this), UnknownFileType.INSTANCE);

      try {
        // execute in impatient mode to not deadlock when the indexing process waits in under write action for queue to load contents in other threads
        // and that other thread asks JspManager for encoding which requires read action for PSI
        ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(() -> LoadTextUtil.detectCharsetAndSetBOM(this, bytes, fileType));
      }
      catch (ApplicationUtil.CannotRunReadActionException ignored) {
      }
    }
    return bytes;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    checkNotTooLarge(requestor);
    return VfsUtilCore.outputStreamAddingBOM(ourPersistence.getOutputStream(this, requestor, modStamp, timeStamp), this);
  }

  @Override
  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    checkNotTooLarge(requestor);
    super.setBinaryContent(content, newModificationStamp, newTimeStamp, requestor);
  }

  @Override
  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp) throws IOException {
    checkNotTooLarge(null);
    super.setBinaryContent(content, newModificationStamp, newTimeStamp);
  }

  @Nullable
  @Override
  public String getDetectedLineSeparator() {
    if (getFlagInt(SYSTEM_LINE_SEPARATOR_DETECTED)) {
      return LineSeparator.getSystemLineSeparator().getSeparatorString();
    }
    return super.getDetectedLineSeparator();
  }

  @Override
  public void setDetectedLineSeparator(String separator) {
    boolean hasSystemSeparator = LineSeparator.getSystemLineSeparator().getSeparatorString().equals(separator);
    setFlagInt(SYSTEM_LINE_SEPARATOR_DETECTED, hasSystemSeparator);
    super.setDetectedLineSeparator(hasSystemSeparator ? null : separator);
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    mySegment.setUserMap(myId, map);
  }

  @NotNull
  @Override
  protected KeyFMap getUserMap() {
    return mySegment.getUserMap(this, myId);
  }

  @Override
  protected boolean changeUserMap(KeyFMap oldMap, KeyFMap newMap) {
    VirtualDirectoryImpl.checkLeaks(newMap);
    return mySegment.changeUserMap(myId, oldMap, UserDataInterner.internUserData(newMap));
  }

  private void checkNotTooLarge(@Nullable Object requestor) throws FileTooBigException {
    if (!(requestor instanceof LargeFileWriteRequestor) && isTooLarge()) throw new FileTooBigException(getPath());
  }

  private boolean isTooLarge() {
    return FileUtilRt.isTooLarge(getLength());
  }
}
