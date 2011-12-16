/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author peter
 */
public class MockVirtualFile extends VirtualFile {
  private VirtualFile myParent;
  private final String myName;
  private final boolean myDirectory;
  private final List<VirtualFile> myChildren = new SmartList<VirtualFile>();
  private String myText;
  private FileType myFileType;
  private final MockVirtualFileSystem myFileSystem = new MockVirtualFileSystem();

  public MockVirtualFile(final String name) {
    this(false, name);
  }

  public MockVirtualFile(final boolean directory, final String name) {
    myDirectory = directory;
    myName = name;
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

  @Override
  public VirtualFile createChildData(final Object requestor, @NotNull @NonNls final String name) {
    final MockVirtualFile file = new MockVirtualFile(name);
    file.setParent(this);
    myChildren.add(file);
    return file;
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public String getPath() {
    String prefix = myParent == null ? "MOCK_ROOT:" : myParent.getPath();
    return prefix + "/" + myName;
  }

  @Override
  public boolean isWritable() {
    throw new UnsupportedOperationException("Method isWritable is not yet implemented in " + getClass().getName());
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
    return (VirtualFile[])VfsUtil.toVirtualFileArray(myChildren);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException("Method getOutputStream is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myText.getBytes();
  }

  @Override
  public long getTimeStamp() {
    throw new UnsupportedOperationException("Method getTimeStamp is not yet implemented in " + getClass().getName());
  }

  @Override
  public long getLength() {
    throw new UnsupportedOperationException("Method getLength is not yet implemented in " + getClass().getName());
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw new UnsupportedOperationException("Method refresh is not yet implemented in " + getClass().getName());
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException("Method getInputStream is not yet implemented in " + getClass().getName());
  }


  
}
