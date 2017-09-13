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
package org.jetbrains.idea.devkit.testAssistant.vfs;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author yole
 */
@Presentation(icon = "AllIcons.Nodes.TestSourceFolder")
public class TestDataGroupVirtualFile extends VirtualFile {
  private final VirtualFile myBeforeFile;
  private final VirtualFile myAfterFile;

  public TestDataGroupVirtualFile(@NotNull VirtualFile beforeFile, @NotNull VirtualFile afterFile) {
    myBeforeFile = beforeFile;
    myAfterFile = afterFile;
  }

  @NotNull
  @Override
  public String getName() {
    return myAfterFile.getName() + " | " + myAfterFile.getName();
  }

  @NotNull
  public VirtualFile getBeforeFile() {
    return myBeforeFile;
  }

  @NotNull
  public VirtualFile getAfterFile() {
    return myAfterFile;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return TestDataGroupFileSystem.getTestDataGroupFileSystem();
  }

  @NotNull
  @Override
  public String getPath() {
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
  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    return ArrayUtil.EMPTY_BYTE_ARRAY;
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
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public FileType getFileType() {
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
