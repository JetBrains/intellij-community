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
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
  private FSItem convert(VirtualFile file) {
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) return myRoot;

    FSItem parentItem = convert(parentFile);
    if (parentItem == null || !parentItem.isDirectory()) {
      return null;
    }

    return parentItem.findChild(file.getName());
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    final FSItem fsItem = convert(parent);
    assert fsItem != null && fsItem.isDirectory();

    final FSDir fsDir = (FSDir)fsItem;
    final FSItem existingDir = fsDir.findChild(dir);
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
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException {
    final FSItem fsItem = convert(parent);
    if (fsItem == null) {
      FSRecords.invalidateCaches();
      throw new IllegalStateException("cannot find parent directory: " + parent.getPath());
    }
    assert fsItem.isDirectory() : "parent is not a directory: " + parent.getPath();

    final FSDir fsDir = (FSDir)fsItem;

    assert fsDir.findChild(file) == null : "File " + file + " already exists in " + parent.getPath();
    fsDir.addChild(new FSFile(fsDir, file));

    return new FakeVirtualFile(parent, file);
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
  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
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
  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
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
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
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
  public void refresh(final boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
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

    protected FSItem(final FSDir parent, final String name) {
      myParent = parent;
      myName = name;
      myTimestamp = LocalTimeCounter.currentTime();
      myWritable = true;
    }

    public abstract boolean isDirectory();

    @Nullable
    public FSItem findChild(final String name) {
      return null;
    }

    public void setName(final String name) {
      myName = name;
    }

    public FSDir getParent() {
      return myParent;
    }

    public String[] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + ": " + myName;
    }
  }

  private static class FSDir extends FSItem {
    private final List<FSItem> myChildren = new ArrayList<>();

    public FSDir(final FSDir parent, final String name) {
      super(parent, name);
    }

    @Override
    @Nullable
    public FSItem findChild(final String name) {
      for (FSItem child : myChildren) {
        if (name.equals(child.myName)) {
          return child;
        }
      }

      return null;
    }

    @Override
    public boolean isDirectory() {
      return true;
    }

    public void addChild(final FSItem item) {
      myChildren.add(item);
    }

    public void removeChild(final FSItem fsItem) {
      if (fsItem.myName.equals("src") && getParent() == null) {
        throw new RuntimeException("removing src directory");
      }
      myChildren.remove(fsItem);
    }

    @Override
    public String[] list() {
      String[] names = ArrayUtil.newStringArray(myChildren.size());
      for (int i = 0; i < names.length; i++) {
        names[i] = myChildren.get(i).myName;
      }
      return names;
    }
  }

  private static class FSFile extends FSItem {
    public FSFile(final FSDir parent, final String name) {
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
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    throw new IncorrectOperationException();
  }

  @Nullable
  @Override
  protected String normalize(@NotNull String path) {
    return path;
  }
}