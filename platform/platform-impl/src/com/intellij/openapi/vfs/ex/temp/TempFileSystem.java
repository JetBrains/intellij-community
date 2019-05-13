/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author max
 */
public class TempFileSystem extends LocalFileSystemBase {
  private final FSItem myRoot = new FSDir(null, "/");

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static TempFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(TempFileSystem.class);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull final String path) {
    return "/";
  }

  @Nullable
  private FSItem convert(@NotNull VirtualFile file) {
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) return myRoot;

    FSItem parentItem = convert(parentFile);
    if (parentItem == null || !parentItem.isDirectory()) {
      return null;
    }

    return parentItem.findChild(file.getName());
  }

  @NotNull
  private FSDir convertDirectory(@NotNull VirtualFile parent) {
    final FSItem fsItem = convert(parent);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new IllegalStateException("cannot find parent directory: " + parent.getPath());
    }
    assert fsItem.isDirectory() : "parent is not a directory: " + parent.getPath();

    return (FSDir)fsItem;
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
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

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) {
    FSDir fsDir = convertDirectory(parent);

    assert fsDir.findChild(file) == null : "File " + file + " already exists in " + parent.getPath();
    fsDir.addChild(new FSFile(fsDir, file));

    return new FakeVirtualFile(parent, file);
  }

  @Nullable
  public VirtualFile findModelChild(@NotNull VirtualFile parent, @NotNull String name) {
    FSItem child = convertDirectory(parent).findChild(name);
    return child == null ? null : new FakeVirtualFile(parent, name);
  }

  @NotNull
  @Override
  public VirtualFile copyFile(Object requestor,
                              @NotNull VirtualFile file,
                              @NotNull VirtualFile newParent,
                              @NotNull String copyName) throws IOException {
    return VfsUtilCore.copyFile(requestor, file, newParent, copyName);
  }

  @Override
  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) {
    final FSItem fsItem = convert(file);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new IllegalStateException("failed to delete file " + file.getPath());
    }
    fsItem.getParent().removeChild(fsItem);
  }

  @Override
  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null : "failed to move file " + file.getPath();
    final FSItem newParentItem = convert(newParent);
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
  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;
    fsItem.setName(newName);
  }

  @Override
  @NotNull
  public String getProtocol() {
    return "temp";
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    return convert(fileOrDirectory) != null;
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;
    return fsItem.list();
  }

  @NotNull
  @Override
  public String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    return convert(file) instanceof FSDir;
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null : "cannot find item for path " + file.getPath();
    return fsItem.myTimestamp;
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long timeStamp) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;
    fsItem.myTimestamp = timeStamp > 0 ? timeStamp : LocalTimeCounter.currentTime();
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;
    return fsItem.myWritable;
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;
    fsItem.myWritable = writableFlag;
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    final FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Cannot find temp for " + file.getPath());
    assert fsItem instanceof FSFile : fsItem;
    return ((FSFile)fsItem).myContent;
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(file));
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file,
                                      final Object requestor,
                                      final long modStamp,
                                      final long timeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        final FSItem fsItem = convert(file);
        assert fsItem instanceof FSFile;

        ((FSFile)fsItem).myContent = toByteArray();
        setTimeStamp(file, modStamp);
      }
    };
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
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

    @Nullable
    public FSItem findChild(@NotNull String name) {
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

    @NotNull
    public String[] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
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
    @Nullable
    public FSItem findChild(@NotNull final String name) {
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

    @NotNull
    @Override
    public String[] list() {
      return ArrayUtil.toStringArray(myChildren.keySet());
    }
  }

  private static class FSFile extends FSItem {
    FSFile(@NotNull FSDir parent, @NotNull String name) {
      super(parent, name);
    }

    private byte[] myContent = new byte[0];

    @Override
    public boolean isDirectory() {
      return false;
    }
  }

  @Override
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    final FSItem item = convert(file);
    if (item == null) return null;
    final long length = item instanceof FSFile ? ((FSFile)item).myContent.length : 0;
    return new FileAttributes(item.isDirectory(), false, false, false, length, item.myTimestamp, item.myWritable);
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<? extends WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  protected String normalize(@NotNull String path) {
    return path;
  }
}