// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

class VfsEventGenerationHelper {
  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker");

  private final List<VFileEvent> myEvents = new ArrayList<>();

  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }

  boolean checkDirty(@NotNull NewVirtualFile file) {
    boolean fileDirty = file.isDirty();
    if (LOG.isTraceEnabled()) LOG.trace("file=" + file + " dirty=" + fileDirty);
    return fileDirty;
  }

  void checkContentChanged(@NotNull VirtualFile file, long oldTimestamp, long newTimestamp, long oldLength, long newLength) {
    if (oldTimestamp != newTimestamp || oldLength != newLength) {
      if (LOG.isTraceEnabled()) LOG.trace(
        "update file=" + file +
        (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
        (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
      myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, oldTimestamp, newTimestamp, oldLength, newLength, true));
    }
  }

  void scheduleCreation(@NotNull VirtualFile parent,
                        @NotNull String childName,
                        @NotNull FileAttributes attributes,
                        String symlinkTarget) {
    if (LOG.isTraceEnabled()) LOG.trace("create parent=" + parent + " name=" + childName + " attr=" + attributes);
    ChildInfo[] children;
    if (attributes.isDirectory() && parent.getFileSystem() instanceof LocalFileSystem && !attributes.isSymLink()) {
      Path root = Paths.get(parent.getPath(), childName);
      Path[] excluded = ContainerUtil.mapNotNull(((ProjectManagerImpl)ProjectManager.getInstance()).getAllExcludedUrls(),
                                         url -> {
                                           Path path = Paths.get(VirtualFileManager.extractPath(url));
                                           return path.startsWith(root) ? path : null;
                                         }, new Path[0]);

      children = scanChildren(root, excluded);
    }
    else {
      children = null;
    }
    VFileCreateEvent event = new VFileCreateEvent(null, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, true, children);
    myEvents.add(event);
  }

  // scan all children of "root" (except excluded dirs) recursively and return them in the ChildInfo[] array
  @Nullable // null means error during scan
  private static ChildInfo[] scanChildren(@NotNull Path root, @NotNull Path[] excluded) {
    // top of the stack contains list of children found so far in the current directory
    Stack<List<ChildInfo>> stack = new Stack<>();
    ChildInfo fakeRoot = new ChildInfo(-1, "", null, null, null);
    stack.push(ContainerUtil.newSmartList(fakeRoot));
    FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(root)) {
          visitFile(dir, attrs);
        }
        // on average, this "excluded" array is very small for any particular root, so linear search it is.
        if (ArrayUtil.contains(dir, excluded)) {
          // do not drill inside excluded root (just record its attributes nevertheless), even if we have content roots beneath
          // stop optimization right here - it's too much pain to track all these nested content/excluded/content otherwise
          return FileVisitResult.SKIP_SUBTREE;
        }
        stack.push(new ArrayList<>());
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = file.getFileName().toString();
        boolean isSymLink = false;
        if (attrs.isSymbolicLink()) {
          // under windows the isDirectory attribute for symlink is incorrect - reread it again
          isSymLink = true;
          attrs = Files.readAttributes(file, BasicFileAttributes.class);
        }
        FileAttributes attributes = LocalFileSystemRefreshWorker.toFileAttributes(file, attrs, isSymLink);
        String symLinkTarget = attributes.isSymLink() ? file.toRealPath().toString() : null;
        ChildInfo info = new ChildInfo(-1, name, attributes, null, symLinkTarget);
        stack.peek().add(info);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        List<ChildInfo> childInfos = stack.pop();
        List<ChildInfo> parentInfos = stack.peek();
        // store children back
        ChildInfo parentInfo = ContainerUtil.getLastItem(parentInfos);
        ChildInfo[] children = childInfos.toArray(ChildInfo.EMPTY_ARRAY);
        ChildInfo newInfo = new ChildInfo(parentInfo.id, parentInfo.name, parentInfo.attributes, children, parentInfo.symLinkTarget);
        parentInfos.set(parentInfos.size() - 1, newInfo);
        return FileVisitResult.CONTINUE;
      }
    };
    try {
      Files.walkFileTree(root, visitor);
    }
    catch (IOException e) {
      LOG.warn(e);
      // tell client we didn't find any children, abandon the optimization altogether
      return null;
    }
    return stack.pop().get(0).children;
  }

  void scheduleDeletion(@NotNull VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  void checkSymbolicLinkChange(@NotNull VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtil.toSystemIndependentName(currentTarget) : null;
    if (!Comparing.equal(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  void checkHiddenAttributeChange(@NotNull VirtualFile child, boolean oldHidden, boolean newHidden) {
    if (oldHidden != newHidden) {
      scheduleAttributeChange(child, VirtualFile.PROP_HIDDEN, oldHidden, newHidden);
    }
  }

  void checkWritableAttributeChange(@NotNull VirtualFile file, boolean oldWritable, boolean newWritable) {
    if (oldWritable != newWritable) {
      scheduleAttributeChange(file, VirtualFile.PROP_WRITABLE, oldWritable, newWritable);
    }
  }

  void scheduleAttributeChange(@NotNull VirtualFile file, @VirtualFile.PropName @NotNull String property, Object current, Object upToDate) {
    if (LOG.isTraceEnabled()) LOG.trace("update file=" + file + ' ' + property + '=' + current + "->" + upToDate);
    myEvents.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }

  void addAllEventsFrom(@NotNull VfsEventGenerationHelper otherHelper) {
    myEvents.addAll(otherHelper.myEvents);
  }
}