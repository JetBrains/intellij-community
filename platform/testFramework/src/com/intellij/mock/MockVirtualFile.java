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
package com.intellij.mock;

import com.intellij.openapi.vfs.*;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author peter
 */
public class MockVirtualFile extends VirtualFile {
  private static final MockVirtualFileSystem ourFileSystem = new MockVirtualFileSystem();

  private VirtualFile myParent;
  private final String myName;
  private final boolean myDirectory;
  private final List<VirtualFile> myChildren = new SmartList<>();
  private String myText;
  private boolean myIsWritable = true;
  private long myModStamp = LocalTimeCounter.currentTime();

  public MockVirtualFile(final String name) {
    this(false, name);
  }

  public MockVirtualFile(final boolean directory, final String name) {
    myDirectory = directory;
    myName = name;
  }

  public MockVirtualFile(String name, String text) {
    myName = name;
    myText = text;
    myDirectory = false;
  }

  public void setText(final String text) {
    myText = text;
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return myName;
  }

  public void setParent(final VirtualFile parent) {
    myParent = parent;
  }

  @NotNull
  @Override
  public VirtualFile createChildData(final Object requestor, @NotNull @NonNls final String name) {
    final MockVirtualFile file = new MockVirtualFile(name);
    addChild(file);
    return file;
  }

  public void addChild(@NotNull final MockVirtualFile child) {
    child.setParent(this);
    myChildren.add(child);
  }

  @Override
  @NotNull
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
  public void setWritable(boolean b) {
    myIsWritable = b;
  }

  @Override
  public boolean isDirectory() {
    return myDirectory;
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
  public VirtualFile[] getChildren() {
    return VfsUtilCore.toVirtualFileArray(myChildren);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
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
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myText.getBytes();
  }


  private final long myTimeStamp = System.currentTimeMillis();
  @Override
  public long getTimeStamp() {
    return myTimeStamp;
  }

  @Override
  public long getLength() {
    return myText.length();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException("Method getInputStream is not yet implemented in " + getClass().getName());
  }

  private VirtualFileListener myListener = null;
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
