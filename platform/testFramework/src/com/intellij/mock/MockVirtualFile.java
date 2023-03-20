// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.vfs.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MockVirtualFile extends VirtualFile {
  public static MockVirtualFile dir(@NotNull String name, MockVirtualFile... children) {
    MockVirtualFile dir = new MockVirtualFile(true, name);
    for (MockVirtualFile child : children) dir.addChild(child);
    return dir;
  }

  public static MockVirtualFile file(@NotNull String name) {
    return new MockVirtualFile(name);
  }

  private static final MockVirtualFileSystem ourFileSystem = new MockVirtualFileSystem();

  private VirtualFile myParent;
  private final String myName;
  private final boolean myDirectory;
  private final List<VirtualFile> myChildren = new SmartList<>();
  private String myText;
  private boolean myIsWritable = true;
  private long myModStamp = LocalTimeCounter.currentTime();
  private final long myTimeStamp = System.currentTimeMillis();
  private VirtualFileListener myListener;

  public MockVirtualFile(String name) {
    this(false, name);
  }

  public MockVirtualFile(boolean directory, String name) {
    myDirectory = directory;
    myName = name;
  }

  public MockVirtualFile(String name, String text) {
    myName = name;
    myText = text;
    myDirectory = false;
  }

  public void setText(String text) {
    myText = text;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void setParent(VirtualFile parent) {
    myParent = parent;
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) {
    MockVirtualFile file = new MockVirtualFile(name);
    addChild(file);
    return file;
  }

  public void addChild(@NotNull MockVirtualFile child) {
    child.setParent(this);
    myChildren.add(child);
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @NotNull
  @Override
  public String getPath() {
    String prefix = myParent == null ? "MOCK_ROOT:" : myParent.getPath();
    return prefix + "/" + myName;
  }

  @Override
  public boolean isWritable() {
    return myIsWritable;
  }

  @Override
  public void setWritable(boolean writable) {
    myIsWritable = writable;
  }

  @Override
  public boolean isDirectory() {
    return myDirectory;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Nullable
  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public VirtualFile[] getChildren() {
    return VfsUtilCore.toVirtualFileArray(myChildren);
  }

  @Override
  public final boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public void close() {
        myModStamp = newModificationStamp;
        myText = toString();
      }
    };
  }

  @Override
  public long getModificationStamp() {
    return myModStamp;
  }

  public void setModificationStamp(long modStamp) {
    myModStamp = modStamp;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    return myText == null ? ArrayUtilRt.EMPTY_BYTE_ARRAY : myText.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public long getTimeStamp() {
    return myTimeStamp;
  }

  @Override
  public long getLength() {
    return myText == null ? 0 : myText.length();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) { }

  @Override
  public @NotNull InputStream getInputStream() {
    throw new UnsupportedOperationException("Method getInputStream is not yet implemented in " + getClass().getName());
  }

  public void setListener(VirtualFileListener listener) {
    myListener = listener;
  }

  public void setContent(@Nullable Object requestor, String content, boolean fireEvent) {
    long oldStamp = myModStamp;
    myText = content;
    if (fireEvent) {
      myModStamp = LocalTimeCounter.currentTime();
      myListener.contentsChanged(new VirtualFileEvent(requestor, this, null, oldStamp, myModStamp));
    }
  }
}