// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshWorker.RefreshCancelledException;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.vfs.newvfs.RefreshWorker.LOG;

final class VfsEventGenerationHelper {
  private final Map<Thread, Pair<List<VFileEvent>, Integer>> myState = new ConcurrentHashMap<>();

  @NotNull List<VFileEvent> getEvents() {
    var events = new ArrayList<VFileEvent>();
    myState.values().forEach(v -> events.addAll(v.first));
    return events;
  }

  private List<VFileEvent> events() {
    return myState.computeIfAbsent(Thread.currentThread(), __ -> new Pair<>(new ArrayList<>(), -1)).first;
  }

  void checkContentChanged(@NotNull VirtualFile file, long oldTimestamp, long newTimestamp, long oldLength, long newLength) {
    if (oldTimestamp != newTimestamp || oldLength != newLength) {
      if (LOG.isTraceEnabled()) LOG.trace(
        "update file=" + file +
        (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
        (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
      events().add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, oldTimestamp, newTimestamp, oldLength, newLength, true));
    }
  }

  void scheduleCreation(@NotNull VirtualFile parent,
                        @NotNull String childName,
                        @NotNull FileAttributes attributes,
                        @Nullable String symlinkTarget,
                        @NotNull ThrowableRunnable<RefreshCancelledException> checkCanceled) throws RefreshCancelledException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("create parent=" + parent + " name=" + childName + " attr=" + attributes);
    }
    ChildInfo[] children = null;
    if (attributes.isDirectory() && parent.getFileSystem() instanceof LocalFileSystem && !attributes.isSymLink()) {
      try {
        Path childPath = getChildPath(parent.getPath(), childName);
        if (childPath != null && shouldScanDirectory(parent, childPath, childName)) {
          List<Path> relevantExcluded = ContainerUtil.mapNotNull(ProjectManagerEx.getInstanceEx().getAllExcludedUrls(), url -> {
            Path path = Path.of(VirtualFileManager.extractPath(url));
            return path.startsWith(childPath) ? path : null;
          });
          children = scanChildren(childPath, relevantExcluded, checkCanceled);
        }
      }
      catch (InvalidPathException e) {
        LOG.warn("Invalid child name: '" + childName + "'", e);
      }
    }
    events().add(new VFileCreateEvent(null, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, true, children));
    VFileEvent event = VfsImplUtil.generateCaseSensitivityChangedEventForUnknownCase(parent, childName);
    if (event != null) {
      events().add(event);
    }
  }

  private static Path getChildPath(String parentPath, String childName) {
    try {
      return Path.of(parentPath, childName);
    }
    catch (InvalidPathException e) {
      LOG.warn("Invalid child name: '" + childName + "'", e);
      return null;
    }
  }

  private static boolean shouldScanDirectory(VirtualFile parent, Path child, String childName) {
    if (FileTypeManager.getInstance().isFileIgnored(childName)) return false;
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      if (ReadAction.compute(() -> ProjectFileIndex.getInstance(openProject).isUnderIgnored(parent))) {
        return false;
      }
      String projectRootPath = openProject.getBasePath();
      if (projectRootPath != null) {
        Path path = Path.of(projectRootPath);
        if (child.startsWith(path)) return true;
      }
    }
    return false;
  }

  void beginTransaction() {
    var thread = Thread.currentThread();
    var state = myState.get(thread);
    myState.put(thread, state != null ? new Pair<>(state.first, state.first.size()) : new Pair<>(new ArrayList<>(), 0));
  }

  void endTransaction(boolean success) {
    var thread = Thread.currentThread();
    var state = myState.get(thread);
    var events = state.first;
    if (!success) {
      events.subList(state.second, events.size()).clear();
    }
    myState.put(thread, new Pair<>(events, -1));
  }

  // scan all children of "root" (except excluded dirs) recursively and return them in the ChildInfo[] array
  // null means error during scan
  private static ChildInfo @Nullable [] scanChildren(Path root, List<Path> excluded, ThrowableRunnable<RefreshCancelledException> checkCanceled) throws RefreshCancelledException {
    // top of the stack contains list of children found so far in the current directory
    Stack<List<ChildInfo>> stack = new Stack<>();
    ChildInfo fakeRoot = new ChildInfoImpl("", null, null, null);
    stack.push(new SmartList<>(fakeRoot));
    FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      int checkCanceledCount;
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(root)) {
          visitFile(dir, attrs);
        }
        if (SystemInfo.isWindows) {
          // Even though Files.walkFileTree does not follow symbolic links, it follows Windows Junctions for some reason.
          // We shouldn't follow any links (including Windows Junctions) to avoid possible performance issues
          // caused by symlink configuration leading to exponential amount of visited files.
          // `BasicFileAttribute` doesn't support Windows Junctions, need to use `FileSystemUtil.getAttributes` for that.
          FileAttributes attributes = FileSystemUtil.getAttributes(dir.toString());
          if (attributes != null && attributes.isSymLink()) {
            return FileVisitResult.SKIP_SUBTREE;
          }
        }
        // on average, this "excluded" array is very small for any particular root, so linear search it is.
        if (excluded.contains(dir)) {
          // do not drill inside excluded root (just record its attributes nevertheless), even if we have content roots beneath
          // stop optimization right here - it's too much pain to track all these nested content/excluded/content otherwise
          return FileVisitResult.SKIP_SUBTREE;
        }
        stack.push(new ArrayList<>());
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if ((++checkCanceledCount & 0xf) == 0) {
          checkCanceled.run();
        }
        String name = file.getFileName().toString();
        FileAttributes attributes = FileSystemUtil.getAttributes(file.toString());
        String symLinkTarget = attrs.isSymbolicLink() ? FileUtil.toSystemIndependentName(file.toRealPath().toString()) : null;
        ChildInfo info = new ChildInfoImpl(name, attributes, null, symLinkTarget);
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
        ChildInfo newInfo = ((ChildInfoImpl)parentInfo).withChildren(children);

        parentInfos.set(parentInfos.size() - 1, newInfo);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        // ignore exceptions when e.g. compiler quickly creates a temp file, FileWalker tries to read its attributes but by then it already deleted
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
    return stack.pop().get(0).getChildren();
  }

  void scheduleDeletion(@NotNull VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    events().add(new VFileDeleteEvent(null, file, true));
  }

  void checkSymbolicLinkChange(@NotNull VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtil.toSystemIndependentName(currentTarget) : null;
    if (!Objects.equals(oldTarget, currentVfsTarget)) {
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
    events().add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }
}
