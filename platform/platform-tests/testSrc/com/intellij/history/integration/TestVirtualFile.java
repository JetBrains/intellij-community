// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.core.Paths;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    return myContent == null ? 0 : myContent.getBytes(StandardCharsets.UTF_8).length;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    return myContent == null ? ArrayUtilRt.EMPTY_BYTE_ARRAY : myContent.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return new MockLocalFileSystem() {
      @Override
      public boolean equals(Object o) {
        return o != null;
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
  public @NotNull InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }
}
