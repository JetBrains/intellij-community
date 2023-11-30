// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant.vfs;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.testAssistant.TestDataUtil;

import java.io.InputStream;
import java.io.OutputStream;

@Presentation(icon = "AllIcons.Nodes.TestSourceFolder")
public final class TestDataGroupVirtualFile extends VirtualFile {
  private final VirtualFile myBeforeFile;
  private final VirtualFile myAfterFile;

  public TestDataGroupVirtualFile(@NotNull VirtualFile beforeFile, @NotNull VirtualFile afterFile) {
    myBeforeFile = beforeFile;
    myAfterFile = afterFile;
  }

  @Override
  public @NotNull String getName() {
    return TestDataUtil.getGroupDisplayName(myBeforeFile.getName(), myAfterFile.getName());
  }

  public @NotNull VirtualFile getBeforeFile() {
    return myBeforeFile;
  }

  public @NotNull VirtualFile getAfterFile() {
    return myAfterFile;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return TestDataGroupFileSystem.getTestDataGroupFileSystem();
  }

  @Override
  public @NotNull String getPath() {
    return TestDataGroupFileSystem.getPath(myBeforeFile, myAfterFile);
  }

  @Override
  public boolean isWritable() {
    return myBeforeFile.isWritable();
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myBeforeFile.isValid() && myAfterFile.isValid();
  }

  @Override
  public VirtualFile getParent() {
    return myBeforeFile.getParent();
  }

  @Override
  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  @Override
  public long getTimeStamp() {
    return myBeforeFile.getTimeStamp();
  }

  @Override
  public long getLength() {
    return myBeforeFile.getLength();
  }

  @Override
  public long getModificationStamp() {
    return myBeforeFile.getModificationStamp();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public @NotNull InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull FileType getFileType() {
    return myBeforeFile.getFileType();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TestDataGroupVirtualFile file = (TestDataGroupVirtualFile)o;

    if (!myAfterFile.equals(file.myAfterFile)) return false;
    if (!myBeforeFile.equals(file.myBeforeFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBeforeFile.hashCode();
    result = 31 * result + myAfterFile.hashCode();
    return result;
  }
}
