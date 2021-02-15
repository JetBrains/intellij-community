// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TempFileSystem extends LocalFileSystemBase implements VirtualFilePointerCapableFileSystem {
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

  private FSDir convertDirectory(VirtualFile parent) {
    FSItem fsItem = convert(parent);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new IllegalStateException("cannot find parent directory: " + parent.getPath());
    }
    assert fsItem.isDirectory() : "parent is not a directory: " + parent.getPath();
    return (FSDir)fsItem;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    FSDir fsDir = convertDirectory(parent);
    FSItem existingDir = fsDir.findChild(dir);
    if (existingDir == null) {
      fsDir.addChild(new FSDir(fsDir, dir));
    }
    else if (!existingDir.isDirectory()) {
      throw new IOException("Directory already contains a file named " + dir);
    }
    return new FakeVirtualFile(parent, dir);
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) {
    FSDir fsDir = convertDirectory(parent);

    assert fsDir.findChild(name) == null : "File " + name + " already exists in " + parent.getPath();
    fsDir.addChild(new FSFile(fsDir, name));

    return new FakeVirtualFile(parent, name);
  }

  public @Nullable VirtualFile findModelChild(@NotNull VirtualFile parent, @NotNull String name) {
    FSItem child = convertDirectory(parent).findChild(name);
    return child == null ? null : new FakeVirtualFile(parent, name);
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) throws IOException {
    return VfsUtilCore.copyFile(requestor, file, newParent, copyName);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new IllegalStateException("failed to delete file " + file.getPath());
    }
    fsItem.getParent().removeChild(fsItem);
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    FSItem fsItem = convert(file);
    assert fsItem != null : "failed to move file " + file.getPath();
    FSItem newParentItem = convert(newParent);
    assert newParentItem != null && newParentItem.isDirectory() : "failed to find move target " + file.getPath();
    FSDir newDir = (FSDir)newParentItem;
    if (newDir.findChild(file.getName()) != null) {
      throw new IOException("Directory already contains a file named " + file.getName());
    }
    fsItem.getParent().removeChild(fsItem);
    newDir.addChild(fsItem);
    fsItem.myParent = newDir;
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) {
    FSItem fsItem = convert(file);
    assert fsItem != null : file;
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
    assert fsItem != null : file;
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
    assert fsItem != null : file;
    return fsItem.myTimestamp;
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    FSItem fsItem = convert(file);
    assert fsItem != null : file;
    fsItem.myTimestamp = timeStamp > 0 ? timeStamp : LocalTimeCounter.currentTime();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    FSItem fsItem = convert(file);
    assert fsItem != null: file;
    return fsItem.myWritable;
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) {
    FSItem fsItem = convert(file);
    assert fsItem != null : file;
    fsItem.myWritable = writableFlag;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Cannot find temp for " + file.getPath());
    assert fsItem instanceof FSFile : fsItem;
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
        assert fsItem instanceof FSFile : fsItem;
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

  private abstract static class FSItem {
    private FSDir myParent;
    private String myName;
    private long myTimestamp;
    private boolean myWritable;

    FSItem(@Nullable FSDir parent, @NotNull String name) {
      myParent = parent;
      myName = name;
      myTimestamp = LocalTimeCounter.currentTime();
      myWritable = true;
    }

    public abstract boolean isDirectory();

    public @Nullable FSItem findChild(@NotNull String name) {
      return null;
    }

    void setName(@NotNull String name) {
      myParent.myChildren.remove(myName);
      myName = name;
      myParent.myChildren.put(name, this);
    }

    public FSDir getParent() {
      return myParent;
    }

    public String @NotNull [] list() {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + ": " + myName;
    }
  }

  private static class FSDir extends FSItem {
    private final Map<String, FSItem> myChildren = new LinkedHashMap<>();

    FSDir(@Nullable FSDir parent, @NotNull String name) {
      super(parent, name);
    }

    @Override
    public @Nullable FSItem findChild(@NotNull String name) {
      return myChildren.get(name);
    }

    @Override
    public boolean isDirectory() {
      return true;
    }

    void addChild(@NotNull FSItem item) {
      myChildren.put(item.myName, item);
    }

    void removeChild(@NotNull FSItem fsItem) {
      if (fsItem.myName.equals("src") && getParent() == null) {
        throw new RuntimeException("removing src directory");
      }
      myChildren.remove(fsItem.myName);
    }

    @Override
    public String @NotNull [] list() {
      return ArrayUtilRt.toStringArray(myChildren.keySet());
    }
  }

  private static class FSFile extends FSItem {
    FSFile(@NotNull FSDir parent, @NotNull String name) {
      super(parent, name);
    }

    private byte[] myContent = ArrayUtil.EMPTY_BYTE_ARRAY;

    @Override
    public boolean isDirectory() {
      return false;
    }
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
  public @NotNull Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                                        @Nullable Collection<String> recursiveRoots,
                                                        @Nullable Collection<String> flatRoots) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull String normalize(@NotNull String path) {
    return path;
  }
}
