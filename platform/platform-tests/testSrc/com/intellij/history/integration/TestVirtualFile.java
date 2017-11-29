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

package com.intellij.history.integration;

import com.intellij.history.core.Paths;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

// todo get rid of!!!!!!!!!!!!!
public class TestVirtualFile extends VirtualFile {
  private final String myName;
  private String myContent;
  private boolean isReadOnly;
  private long myTimestamp;

  private final boolean IsDirectory;
  private VirtualFile myParent;
  private final List<TestVirtualFile> myChildren = new ArrayList<>();

  public TestVirtualFile(@NotNull String name, String content, long timestamp) {
    this(name, content,  timestamp, false);
  }

  public TestVirtualFile(@NotNull String name, String content, long timestamp, boolean isReadOnly) {
    assert !name.contains("/");
    myName = name;
    myContent = content;
    this.isReadOnly = isReadOnly;
    myTimestamp = timestamp;
    IsDirectory = false;
  }

  public TestVirtualFile(String name) {
    myName = name;
    IsDirectory = true;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  protected boolean nameEquals(@NotNull String name) {
    return Paths.isCaseSensitive() ? myName.equals(name) : myName.equalsIgnoreCase(name);
  }

  @Override
  public boolean isDirectory() {
    return IsDirectory;
  }

  @NotNull
  @Override
  public String getPath() {
    if (myParent == null) return myName;
    return myParent.getPath() + "/" + myName;
  }

  @Override
  public long getTimeStamp() {
    return myTimestamp;
  }

  @Override
  public VirtualFile[] getChildren() {
    return VfsUtil.toVirtualFileArray(myChildren);
  }

  public void addChild(TestVirtualFile f) {
    f.myParent = this;
    myChildren.add(f);
  }

  @Override
  public long getLength() {
    return myContent == null ? 0 : myContent.getBytes().length;
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() {
    return myContent == null ? ArrayUtil.EMPTY_BYTE_ARRAY : myContent.getBytes();
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return new MockLocalFileSystem() {
      @Override
      public boolean equals(Object o) {
        return true;
      }
    };
  }

  @Override
  public boolean isWritable() {
    return !isReadOnly;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  @Nullable
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }
}
