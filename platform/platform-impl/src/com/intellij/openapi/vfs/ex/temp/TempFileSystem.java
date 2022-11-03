// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TempFileSystem extends LocalFileSystemBase implements VirtualFilePointerCapableFileSystem, TempFileSystemMarker {
  private static final String TEMP_PROTOCOL = "temp";

  private final FSItem myRoot = new FSDir(null, "/");

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static TempFileSystem getInstance() {
    return (TempFileSystem)VirtualFileManager.getInstance().getFileSystem(TEMP_PROTOCOL);
  }

  @Override
  protected @NotNull String extractRootPath(@NotNull String normalizedPath) {
    return "/";
  }

  @Override
  public @Nullable Path getNioPath(@NotNull VirtualFile file) {
    return null;
  }

  private @Nullable FSItem convert(VirtualFile file) {
    VirtualFile parentFile = file.getParent();
    if (parentFile == null) return myRoot;

    FSItem parentItem = convert(parentFile);
    if (parentItem == null || !parentItem.isDirectory()) {
      return null;
    }

    return parentItem.findChild(file.getName());
  }

  private FSDir convertDirectory(VirtualFile parent) throws IOException {
    FSItem fsItem = convert(parent);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new FileNotFoundException("Does not exist: " + parent.getPath());
    }
    if (!fsItem.isDirectory()) throw new IOException("Not a directory: " + parent.getPath());
    return (FSDir)fsItem;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    FSItem existing = fsDir.findChild(name);
    if (existing == null) {
      fsDir.addChild(new FSDir(fsDir, name));
    }
    else if (!existing.isDirectory()) {
      throw new IOException("File " + name + " already exists in " + parent.getPath());
    }
    return new FakeVirtualFile(parent, name);
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) != null) throw new IOException("File " + name + " already exists in " + parent.getPath());
    fsDir.addChild(new FSFile(fsDir, name));
    return new FakeVirtualFile(parent, name);
  }

  @TestOnly
  public void createIfNotExists(@NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) == null) {
      fsDir.addChild(new FSFile(fsDir, name));
    }
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) throws IOException {
    return VfsUtilCore.copyFile(requestor, file, newParent, copyName);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    FSItem fsItem = convert(file);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new FileNotFoundException("Does not exist: " + file.getPath());
    }
    fsItem.getParent().removeChild(fsItem);
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Source does not exist: " + file.getPath());
    FSItem newParentItem = convert(newParent);
    if (newParentItem == null) throw new FileNotFoundException("Target does not exist: " + file.getPath());
    if (!newParentItem.isDirectory()) throw new IOException("Target is not a directory: " + file.getPath());
    FSDir newDir = (FSDir)newParentItem;
    if (newDir.findChild(file.getName()) != null) throw new IOException("Directory already contains a file named " + file.getName());
    fsItem.getParent().removeChild(fsItem);
    newDir.addChild(fsItem);
    fsItem.myParent = newDir;
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Does not exist: " + file.getPath());
    fsItem.setName(newName);
  }

  @Override
  public @NotNull String getProtocol() {
    return TEMP_PROTOCOL;
  }

  @Override
  public boolean exists(@NotNull VirtualFile fileOrDirectory) {
    return convert(fileOrDirectory) != null;
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new IllegalStateException("Does not exist: " + file.getPath());
    return fsItem.list();
  }

  @Override
  public @NotNull String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    return convert(file) instanceof FSDir;
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new IllegalStateException("Does not exist: " + file.getPath());
    return fsItem.myTimestamp;
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new IllegalStateException("Does not exist: " + file.getPath());
    fsItem.myTimestamp = timeStamp > 0 ? timeStamp : LocalTimeCounter.currentTime();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new IllegalStateException("Does not exist: " + file.getPath());
    return fsItem.myWritable;
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new IllegalStateException("Does not exist: " + file.getPath());
    fsItem.myWritable = writableFlag;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Does not exist: " + file.getPath());
    if (!(fsItem instanceof FSFile)) throw new IOException("Not a file: " + file.getPath());
    return ((FSFile)fsItem).myContent;
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(file));
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        FSItem fsItem = convert(file);
        if (fsItem == null) throw new FileNotFoundException("Does not exist: " + file.getPath());
        if (!(fsItem instanceof FSFile)) throw new IOException("Not a file: " + file.getPath());
        ((FSFile)fsItem).myContent = toByteArray();
        setTimeStamp(file, modStamp);
      }
    };
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    try {
      return contentsToByteArray(file).length;
    }
    catch (IOException e) {
      return 0;
    }
  }

  private abstract static sealed class FSItem {
    private FSDir myParent;
    private String myName;
    private long myTimestamp;
    private boolean myWritable;

    private FSItem(@Nullable("only the root") FSDir parent, String name) {
      myParent = parent;
      myName = name;
      myTimestamp = LocalTimeCounter.currentTime();
      myWritable = true;
    }

    protected boolean isDirectory() {
      return false;
    }

    protected FSItem findChild(String name) {
      return null;
    }

    private void setName(String name) {
      myParent.myChildren.remove(myName);
      myName = name;
      myParent.myChildren.put(name, this);
    }

    protected FSDir getParent() {
      return myParent;
    }

    protected String[] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + ": " + myName;
    }
  }

  private final static class FSDir extends FSItem {
    private final Map<String, FSItem> myChildren = new LinkedHashMap<>();

    private FSDir(@Nullable FSDir parent, String name) {
      super(parent, name);
    }

    @Override
    protected @Nullable FSItem findChild(String name) {
      return myChildren.get(name);
    }

    @Override
    protected boolean isDirectory() {
      return true;
    }

    private void addChild(FSItem item) {
      myChildren.put(item.myName, item);
    }

    private void removeChild(FSItem fsItem) {
      if (fsItem.myName.equals("src") && getParent() == null) {
        throw new RuntimeException("removing 'temp:///src' directory");
      }
      myChildren.remove(fsItem.myName);
    }

    @Override
    protected String[] list() {
      return ArrayUtil.toStringArray(myChildren.keySet());
    }
  }

  private final static class FSFile extends FSItem {
    private FSFile(FSDir parent, String name) {
      super(parent, name);
    }

    private byte[] myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    FSItem item = convert(file);
    if (item == null) return null;
    long length = item instanceof FSFile ? ((FSFile)item).myContent.length : 0;
    // let's make TempFileSystem case-sensitive
    return new FileAttributes(item.isDirectory(), false, false, false, length, item.myTimestamp, item.myWritable, FileAttributes.CaseSensitivity.SENSITIVE);
  }

  @Override
  public @NotNull Set<LocalFileSystem.WatchRequest> replaceWatchedRoots(@NotNull Collection<LocalFileSystem.WatchRequest> watchRequests,
                                                                        @Nullable Collection<String> recursiveRoots,
                                                                        @Nullable Collection<String> flatRoots) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull String normalize(@NotNull String path) {
    return path;
  }
}
