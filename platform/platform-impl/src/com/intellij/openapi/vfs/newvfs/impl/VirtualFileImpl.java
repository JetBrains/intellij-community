// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import java.util.function.Supplier;

public final class VirtualFileImpl extends VirtualFileSystemEntry {
  VirtualFileImpl(int id, @NotNull VfsData.Segment segment, VirtualDirectoryImpl parent) {
    super(id, segment, parent);
    registerLink(getFileSystem());
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
    return Collections.emptyList();
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
  public <T> T computeWithPreloadedContentHint(byte @NotNull [] preloadedContentHint, @NotNull Supplier<? extends T> computable) {
    putUserData(ourPreloadedContentKey, preloadedContentHint);
    try {
      return computable.get();
    }
    finally {
      putUserData(ourPreloadedContentKey, null);
    }
  }

  @Override
  @NotNull
  public InputStream getInputStream() throws IOException {
    final byte[] preloadedContent = getUserData(ourPreloadedContentKey);

    return VfsUtilCore.inputStreamSkippingBOM(
      preloadedContent == null ?
      getPersistence().getInputStream(this) :
      new DataInputStream(new UnsyncByteArrayInputStream(preloadedContent)),
      this
    );
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    return contentsToByteArray(true);
  }

  @Override
  public byte @NotNull [] contentsToByteArray(boolean cacheContent) throws IOException {
    checkNotTooLarge(null);
    final byte[] preloadedContent = getUserData(ourPreloadedContentKey);
    if (preloadedContent != null) return preloadedContent;
    byte[] bytes = getPersistence().contentsToByteArray(this, cacheContent);
    if (!isCharsetSet()) {
      // optimisation: take the opportunity to not load bytes again in getCharset()
      // use getByFile() to not fall into recursive trap from vfile.getFileType() which would try to load contents again to detect charset
      FileType fileType = ObjectUtils.notNull(((FileTypeManagerEx)FileTypeManager.getInstance()).getByFile(this), UnknownFileType.INSTANCE);

      if (fileType != UnknownFileType.INSTANCE && !fileType.isBinary() && bytes.length != 0) {
        try {
          // execute in impatient mode to not deadlock when the indexing process waits under write action for the queue to load contents in other threads
          // and that other thread asks JspManager for encoding which requires read action for PSI
          ((ApplicationEx)ApplicationManager.getApplication())
            .executeByImpatientReader(() -> LoadTextUtil.detectCharsetAndSetBOM(this, bytes, fileType));
        }
        catch (ProcessCanceledException ignored) {
        }
      }
    }
    return bytes;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    checkNotTooLarge(requestor);
    return VfsUtilCore.outputStreamAddingBOM(getPersistence().getOutputStream(this, requestor, modStamp, timeStamp), this);
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    checkNotTooLarge(requestor);
    // NB not using VirtualFile.getOutputStream() to avoid unneeded BOM skipping/writing
    try (OutputStream outputStream = getPersistence().getOutputStream(this, requestor, newModificationStamp, newTimeStamp)) {
      outputStream.write(content);
    }
  }

  @Nullable
  @Override
  public String getDetectedLineSeparator() {
    if (isDirectory()) {
      throw new IllegalArgumentException("getDetectedLineSeparator() must not be called for a directory");
    }
    if (getFlagInt(VfsDataFlags.SYSTEM_LINE_SEPARATOR_DETECTED)) {
      // optimization: do not waste space in user data for system line separator
      return LineSeparator.getSystemLineSeparator().getSeparatorString();
    }
    return super.getDetectedLineSeparator();
  }

  @Override
  public void setDetectedLineSeparator(String separator) {
    if (isDirectory()) {
      throw new IllegalArgumentException("setDetectedLineSeparator() must not be called for a directory");
    }
    // optimization: do not waste space in user data for system line separator
    boolean hasSystemSeparator = LineSeparator.getSystemLineSeparator().getSeparatorString().equals(separator);
    setFlagInt(VfsDataFlags.SYSTEM_LINE_SEPARATOR_DETECTED, hasSystemSeparator);

    super.setDetectedLineSeparator(hasSystemSeparator ? null : separator);
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    getSegment().setUserMap(myId, map);
  }

  @NotNull
  @Override
  protected KeyFMap getUserMap() {
    return getSegment().getUserMap(this, myId);
  }

  @Override
  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    VirtualDirectoryImpl.checkLeaks(newMap);
    return getSegment().changeUserMap(myId, oldMap, UserDataInterner.internUserData(newMap));
  }

  private void checkNotTooLarge(@Nullable Object requestor) throws FileTooBigException {
    if (!(requestor instanceof LargeFileWriteRequestor) && FileUtilRt.isTooLarge(getLength())) throw new FileTooBigException(getPath());
  }

  @Override
  public boolean isCaseSensitive() {
    return getParent().isCaseSensitive();
  }
}
