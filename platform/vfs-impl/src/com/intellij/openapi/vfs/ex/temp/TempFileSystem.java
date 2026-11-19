// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
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

@ApiStatus.Internal
@SuppressWarnings({"removal", "UnnecessaryFullyQualifiedName"})
public class TempFileSystem extends com.intellij.openapi.vfs.impl.local.LocalFileSystemBase implements VirtualFilePointerCapableFileSystem, TempFileSystemMarker {
  private static final String TEMP_PROTOCOL = "temp";

  private final FSItem myRoot = new FSDir();

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static @NotNull TempFileSystem getInstance() {
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

  /**
   * The most links that one path lookup passes through. Same as Linux limit, see {@code MAXSYMLINKS} in
   * {@code include/linux/namei.h} of the kernel.
   */
  private static final int MAX_SYMLINKS = 40;

  /** The item of {@code file}, with each link resolved, also the item of {@code file} itself. Null for a missing or broken link. */
  private @Nullable FSItem convert(@NotNull VirtualFile file) {
    return resolve(convertLink(file));
  }

  /** The item of {@code file}. A link in its parent path is resolved, but a link at {@code file} itself is not. */
  private @Nullable FSItem convertLink(@NotNull VirtualFile file) {
    var parentFile = file.getParent();
    if (parentFile == null) {
      return myRoot;
    }
    var item = file.getUserData(FS_ITEM_KEY);
    if (item == null) {
      var parentItem = convert(parentFile);
      if (parentItem == null || !parentItem.isDirectory()) {
        return null;
      }
      item = parentItem.findChild(file.getName());
      registerFSItem(file, item);
    }
    return item;
  }

  /** The item that {@code item} points to, after each link. Null for a broken link or a loop of links. */
  private @Nullable FSItem resolve(@Nullable FSItem item) {
    if (!(item instanceof FSSymlink link)) return item;
    var target = realItem(link.myTarget, 1);
    return target == null ? null : target.item();
  }

  /** An item and its absolute path, which passes through no link. */
  private record RealItem(@NotNull String path, @NotNull FSItem item) {
  }

  /**
   * The item at the absolute {@code path}, after each link of the path, and the path of the item without links. Null if an
   * item of the path is missing, or if the path passes through more than {@link #MAX_SYMLINKS} links. {@code linkCount}
   * is the count of the links before {@code path}. A VFS path has no escape: {@code /} separates the names, and
   * {@link #isValidName} rejects a name with it. A link target has no {@code .} or {@code ..} names, see {@link #createSymbolicLink}.
   */
  private @Nullable RealItem realItem(@NotNull String path, int linkCount) {
    var realPath = new StringBuilder();
    FSItem item = myRoot;
    for (var name : StringUtil.split(path, "/")) {
      if (!item.isDirectory()) return null;
      var child = item.findChild(name);
      if (child == null) return null;
      if (child instanceof FSSymlink link) {
        if (linkCount == MAX_SYMLINKS) return null;
        var target = realItem(link.myTarget, linkCount + 1);
        if (target == null) return null;
        realPath.setLength(0);
        realPath.append(target.path().equals("/") ? "" : target.path());
        child = target.item();
      }
      else {
        realPath.append('/').append(name);
      }
      item = child;
    }
    return new RealItem(realPath.isEmpty() ? "/" : realPath.toString(), item);
  }

  private @NotNull FSDir convertDirectory(@NotNull VirtualFile dir) throws IOException {
    var fsItem = convertAndCheck(dir);
    if (!fsItem.isDirectory()) {
      throw new IOException("Not a directory: " + dir.getPath());
    }
    return (FSDir)fsItem;
  }

  private @NotNull FSItem convertLinkAndCheck(@NotNull VirtualFile file) {
    var fsItem = convertLink(file);
    if (fsItem == null) {
      throw new IllegalStateException("Does not exist: " + file.getPath());
    }
    return fsItem;
  }

  private @NotNull FSItem convertAndCheck(@NotNull VirtualFile file) {
    var fsItem = convert(file);
    if (fsItem == null) {
      //MAYBE RC: NoSuchFileException seems to fit better:
      // 1. it is an IOException -- no need to catch out-of-nothing IllegalStateException (see LightPlatformTestCase.tearDownSourceRoot())
      // 2. it is thrown by LocalFileSystemBase in case there is no such file -- more unified
      throw new IllegalStateException("Does not exist: " + file.getPath());
    }
    return fsItem;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    var fsDir = convertDirectory(parent);
    var existing = fsDir.findChild(name);
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
    var fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) != null) throw new IOException("File " + name + " already exists in " + parent.getPath());
    fsDir.addChild(name, new FSFile());
    return new FakeVirtualFile(parent, name);
  }

  /**
   * Creates the symbolic link {@code name} in {@code parent} to {@code target}, an absolute path or a path relative to
   * {@code parent}. The target does not have to exist.
   * Like a link of the local file system, the VFS sees the link after a refresh of {@code parent}.
   */
  @TestOnly
  public void createSymbolicLink(@NotNull VirtualFile parent, @NotNull String name, @NotNull String target) throws IOException {
    var fsDir = convertDirectory(parent);
    if (fsDir.findChild(name) != null) throw new IOException("File " + name + " already exists in " + parent.getPath());
    var absoluteTarget = target.startsWith("/") ? target : parent.getPath() + "/" + target;
    fsDir.addChild(name, new FSSymlink(FileUtil.toCanonicalPath(absoluteTarget)));
  }

  @TestOnly
  public void createIfNotExists(@NotNull VirtualFile parent, @NotNull String name) throws IOException {
    var fsDir = convertDirectory(parent);
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
    var parent = convertAndCheckParent(file);
    parent.removeChild(file.getName(), file.getParent());
    clearFsItemCache(file);
  }

  private @NotNull FSDir convertAndCheckParent(@NotNull VirtualFile file) {
    return (FSDir)convertAndCheck(file.getParent());
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    var fsItem = convertLinkAndCheck(file);
    var newParentItem = convertAndCheck(newParent);
    var oldParentItem = convertAndCheckParent(file);
    if (!newParentItem.isDirectory()) throw new IOException("Target is not a directory: " + file.getPath());
    var newDir = (FSDir)newParentItem;
    var name = file.getName();
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
    return convertLink(fileOrDirectory) != null;
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    var fsItem = convertAndCheck(file);
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
    var fsItem = convertAndCheck(file);
    return fsItem.myTimestamp;
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    var fsItem = convertAndCheck(file);
    fsItem.myTimestamp = timeStamp > 0 ? timeStamp : currentTime();
  }

  private static long currentTime() {
    return System.currentTimeMillis();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    var fsItem = convertAndCheck(file);
    return fsItem.myWritable;
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) {
    var fsItem = convertAndCheck(file);
    fsItem.myWritable = writableFlag;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    var fsItem = convertAndCheck(file);
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
        var fsItem = convertAndCheck(file);
        if (!(fsItem instanceof FSFile)) throw new IOException("Not a file: " + file.getPath());
        ((FSFile)fsItem).myContent = toByteArray();
        setTimeStamp(file, timeStamp);
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
    private long myTimestamp = currentTime();
    private boolean myWritable = true;

    protected boolean isDirectory() {
      return false;
    }

    protected @Nullable FSItem findChild(String name) {
      return null;
    }

    protected String[] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  private static final class FSDir extends FSItem {
    private final Map<String, FSItem> myChildren = new LinkedHashMap<>();

    @Override
    protected @Nullable FSItem findChild(String name) {
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
    protected String[] list() {
      return ArrayUtil.toStringArray(myChildren.keySet());
    }
  }

  private static final class FSFile extends FSItem {
    private byte[] myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  /** A symbolic link to the absolute path {@link #myTarget}. */
  private static final class FSSymlink extends FSItem {
    private final String myTarget;

    private FSSymlink(@NotNull String target) {
      myTarget = target;
    }
  }

  private void setName(@NotNull VirtualFile file, @NotNull String name) {
    var parent = convertAndCheckParent(file);
    var fsItem = convertLinkAndCheck(file);
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
    var link = convertLink(file);
    if (link == null) return null;
    var item = resolve(link);
    if (item == null) return FileAttributes.BROKEN_SYMLINK;
    long length = item instanceof FSFile fsFile ? fsFile.myContent.length : 0;
    // let's make TempFileSystem case-sensitive
    return new FileAttributes(item.isDirectory(), false, link instanceof FSSymlink, false, length, item.myTimestamp, item.myWritable,
                              FileAttributes.CaseSensitivity.SENSITIVE);
  }

  @Override
  public @Nullable String resolveSymLink(@NotNull VirtualFile file) {
    var target = realItem(file.getPath(), 0);
    return target == null ? null : target.path();
  }

  @Override
  public @NotNull Set<LocalFileSystem.WatchRequest> replaceWatchedRoots(
    @NotNull Collection<LocalFileSystem.WatchRequest> watchRequests,
    @Nullable Collection<String> recursiveRoots,
    @Nullable Collection<String> flatRoots
  ) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull String normalize(@NotNull String path) {
    return path;
  }
}
