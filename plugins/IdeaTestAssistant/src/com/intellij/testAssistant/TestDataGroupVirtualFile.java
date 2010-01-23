/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testAssistant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.DeprecatedVirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author yole
 */
public class TestDataGroupVirtualFile extends DeprecatedVirtualFile {
  private final VirtualFile myBeforeFile;
  private final VirtualFile myAfterFile;

  public TestDataGroupVirtualFile(VirtualFile beforeFile, VirtualFile afterFile) {
    myBeforeFile = beforeFile;
    myAfterFile = afterFile;
  }

  @NotNull
  @Override
  public String getName() {
    final String prefix = StringUtil.commonPrefix(myBeforeFile.getName(), myAfterFile.getName());
    if (prefix.length() == 0) {
      return StringUtil.commonSuffix(myBeforeFile.getName(), myAfterFile.getName());
    }
    return prefix + "." + myBeforeFile.getExtension();
  }

  public VirtualFile getBeforeFile() {
    return myBeforeFile;
  }

  public VirtualFile getAfterFile() {
    return myAfterFile;
  }

  @Override
  public Icon getIcon() {
    return Icons.TEST_SOURCE_FOLDER;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return LocalFileSystem.getInstance();
  }

  @Override
  public String getPath() {
    return myBeforeFile.getPath();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return null;
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
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }
}
