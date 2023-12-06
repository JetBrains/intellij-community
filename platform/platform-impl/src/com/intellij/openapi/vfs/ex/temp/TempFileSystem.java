// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TempFileSystem extends LocalFileSystemBase implements VirtualFilePointerCapableFileSystem, TempFileSystemMarker {
  private static final String TEMP_PROTOCOL = "temp";

  private final FSItem myRoot = new FSDir();

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

  private static final Key<FSItem> FS_ITEM_KEY = Key.create("FS_ITEM_KEY");

  private @Nullable FSItem convert(@NotNull VirtualFile file) {
    VirtualFile parentFile = file.getParent();
    if (parentFile == null) {
      return myRoot;
    }
    FSItem item = file.getUserData(FS_ITEM_KEY);
    if (item == null) {
      FSItem parentItem = convert(parentFile);
      if (parentItem == null || !parentItem.isDirectory()) {
        return null;
      }
      item = parentItem.findChild(file.getName());
      registerFSItem(file, item);
    }
    return item;
  }

  private @NotNull FSDir convertDirectory(@NotNull VirtualFile dir) throws IOException {
    FSItem fsItem = convertAndCheck(dir);
    if (!fsItem.isDirectory()) {
      String message = "Not a directory: " + dir.getPath();
      final IOException exception = new IOException(message);
      FSRecords.invalidateCaches(message, exception);
      throw exception;
    }
    return (FSDir)fsItem;
  }

  private @NotNull FSItem convertAndCheck(@NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    if (fsItem == null) {
      String message = "Does not exist: " + file.getPath();
      final IllegalStateException exception = new IllegalStateException(message);
      FSRecords.invalidateCaches(message, exception);
      throw exception;
    }
    return fsItem;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    FSItem existing = fsDir.findChild(name);
    if (existing == null) {
      fsDir.addChild(name, new FSDir());
    }
    else if (!existing.isDirectory()) {
      throw new IOException("File " + name + " already exists in " + parent.getPath());
    }
    return new FakeVirtualFile(parent, name);
  }

  private static void registerFSItem(@NotNull VirtualFile parent, FSItem item) {
    if (!(parent instanceof StubVirtualFile)) {
      parent.putUserData(FS_ITEM_KEY, item);
    }
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) != null) throw new IOException("File " + name + " already exists in " + parent.getPath());
    fsDir.addChild(name, new FSFile());
    return new FakeVirtualFile(parent, name);
  }

  @TestOnly
  public void createIfNotExists(@NotNull VirtualFile parent, @NotNull String name) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) == null) {
      fsDir.addChild(name, new FSFile());
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
    FSDir parent = convertAndCheckParent(file);
    parent.removeChild(file.getName(), file.getParent());
    clearFsItemCache(file);
  }

  private @NotNull FSDir convertAndCheckParent(@NotNull VirtualFile file) {
    return (FSDir)convertAndCheck(file.getParent());
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    FSItem fsItem = convertAndCheck(file);
    FSItem newParentItem = convertAndCheck(newParent);
    FSDir oldParentItem = convertAndCheckParent(file);
    if (!newParentItem.isDirectory()) throw new IOException("Target is not a directory: " + file.getPath());
    FSDir newDir = (FSDir)newParentItem;
    String name = file.getName();
    if (newDir.findChild(name) != null) throw new IOException("Directory already contains a file named " + name);
    oldParentItem.removeChild(name, file.getParent());
    newDir.addChild(name, fsItem);
    clearFsItemCache(file);
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    setName(file, newName);
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
    FSItem fsItem = convertAndCheck(file);
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
    FSItem fsItem = convertAndCheck(file);
    return fsItem.myTimestamp;
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    FSItem fsItem = convertAndCheck(file);
    fsItem.myTimestamp = timeStamp > 0 ? timeStamp : LocalTimeCounter.currentTime();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    FSItem fsItem = convertAndCheck(file);
    return fsItem.myWritable;
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) {
    FSItem fsItem = convertAndCheck(file);
    fsItem.myWritable = writableFlag;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    FSItem fsItem = convertAndCheck(file);
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
        FSItem fsItem = convertAndCheck(file);
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
    private long myTimestamp = LocalTimeCounter.currentTime();
    private boolean myWritable = true;

    protected boolean isDirectory() {
      return false;
    }

    protected FSItem findChild(@NotNull String name) {
      return null;
    }

    protected @NotNull String @NotNull [] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  private static final class FSDir extends FSItem {
    private final Map<String, FSItem> myChildren = new LinkedHashMap<>();

    @Override
    protected @Nullable FSItem findChild(@NotNull String name) {
      return myChildren.get(name);
    }

    @Override
    protected boolean isDirectory() {
      return true;
    }

    private void addChild(@NotNull String name, @NotNull FSItem item) {
      myChildren.put(name, item);
    }

    private void removeChild(@NotNull String name, @Nullable VirtualFile parent) {
      if (name.equals("src") && parent == null) {
        throw new RuntimeException("removing 'temp:///src' directory");
      }
      myChildren.remove(name);
    }

    @Override
    protected @NotNull String @NotNull [] list() {
      return ArrayUtil.toStringArray(myChildren.keySet());
    }
  }

  private static final class FSFile extends FSItem {
    private byte[] myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  private void setName(@NotNull VirtualFile file, @NotNull String name) {
    FSDir parent = convertAndCheckParent(file);
    FSItem fsItem = convertAndCheck(file);
    parent.myChildren.remove(file.getName());
    parent.myChildren.put(name, fsItem);
    clearFsItemCache(file.getParent());
    clearFsItemCache(file);
  }

  private static void clearFsItemCache(VirtualFile file) {
    registerFSItem(file, null);
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
