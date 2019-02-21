// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.Queue;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.vfs.newvfs.persistent.VfsEventGenerationHelper.LOG;

class LocalFileSystemRefreshWorker {
  private final boolean myIsRecursive;
  private final Queue<NewVirtualFile> myRefreshQueue = new Queue<>(100);
  private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
  private volatile boolean myCancelled;

  LocalFileSystemRefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myHelper.getEvents();
  }

  public void cancel() {
    myCancelled = true;
  }

  public void scan() {
    NewVirtualFile root = myRefreshQueue.pullFirst();
    boolean rootDirty = root.isDirty();
    if (LOG.isDebugEnabled()) LOG.debug("root=" + root + " dirty=" + rootDirty);
    if (!rootDirty) return;

    NewVirtualFileSystem fs = root.getFileSystem();
    FileAttributes rootAttributes = fs.getAttributes(root);
    if (rootAttributes == null) {
      myHelper.scheduleDeletion(root);
      root.markClean();
      return;
    }
    if (rootAttributes.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }

    myRefreshQueue.addLast(root);

    try {
      processQueue(fs, PersistentFS.getInstance());
    }
    catch (RefreshCancelledException e) {
      LOG.debug("refresh cancelled");
    }
  }

  private void processQueue(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistence) throws RefreshCancelledException {
    TObjectHashingStrategy<String> strategy = FilePathHashingStrategy.create(fs.isCaseSensitive());

    while (!myRefreshQueue.isEmpty()) {
      NewVirtualFile file = myRefreshQueue.pullFirst();
      if (!myHelper.checkDirty(file)) continue;

      checkCancelled(file);

      if (file.isDirectory()) {
        boolean fullSync = ((VirtualDirectoryImpl)file).allChildrenLoaded();
        if (fullSync) {
          fullDirRefresh(fs, persistence, strategy, (VirtualDirectoryImpl)file);
        }
        else {
          partialDirRefresh(fs, persistence, strategy, (VirtualDirectoryImpl)file);
        }
      }
      else {
        refreshFile(fs, persistence, strategy, file);
      }

      if (myIsRecursive || !file.isDirectory()) {
        file.markClean();
      }
    }
  }

  private void refreshFile(@NotNull NewVirtualFileSystem fs,
                           @NotNull PersistentFS persistence,
                           @NotNull TObjectHashingStrategy<String> strategy,
                           @NotNull NewVirtualFile file) {
    RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(file, persistence, fs,
                                                                            null,
                                                                            Collections.singletonList(file), strategy);

    refreshingFileVisitor.visit(file);
    myHelper.addAllEventsFrom(refreshingFileVisitor.getHelper());
  }

  private static final AtomicInteger myRequests = new AtomicInteger();
  private static final AtomicLong myTime = new AtomicLong();

  private void fullDirRefresh(@NotNull NewVirtualFileSystem fs,
                              @NotNull PersistentFS persistence,
                              @NotNull TObjectHashingStrategy<String> strategy,
                              @NotNull VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<String[], VirtualFile[]> result = getDirectorySnapshot(persistence, dir);
      if (result == null) return;
      String[] currentNames = result.getFirst();
      VirtualFile[] children = result.getSecond();

      RefreshingFileVisitor refreshingFileVisitor =
        new RefreshingFileVisitor(dir, persistence, fs, null, Arrays.asList(children), strategy);

      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (ApplicationManager.getApplication().isDisposed()) {
          return true;
        }
        if (!Arrays.equals(currentNames, persistence.list(dir)) || !Arrays.equals(children, dir.getChildren())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        myHelper.addAllEventsFrom(refreshingFileVisitor.getHelper());
        return true;
      });
      if (hasEvents) {
        break;
      }
    }
  }

  static Pair<String[], VirtualFile[]> getDirectorySnapshot(@NotNull PersistentFS persistence, @NotNull VirtualDirectoryImpl dir) {
    return ReadAction.compute(() -> {
      if (ApplicationManager.getApplication().isDisposed()) {
        return null;
      }
      return Pair.create(persistence.list(dir), dir.getChildren());
    });
  }

  private void partialDirRefresh(@NotNull NewVirtualFileSystem fs,
                                 @NotNull PersistentFS persistence,
                                 @NotNull TObjectHashingStrategy<String> strategy,
                                 @NotNull VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<List<VirtualFile>, List<String>> result =
        ReadAction.compute(() -> Pair.create(dir.getCachedChildren(), dir.getSuspiciousNames()));

      List<VirtualFile> cached = result.getFirst();
      List<String> wanted = result.getSecond();

      if (cached.isEmpty() && wanted.isEmpty()) return;
      RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(dir, persistence, fs, wanted, cached, strategy);
      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (!cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        myHelper.addAllEventsFrom(refreshingFileVisitor.getHelper());

        return true;
      });
      if (hasEvents) {
        break;
      }
    }
  }

  private static class RefreshCancelledException extends RuntimeException {
  }

  private void checkCancelled(@NotNull NewVirtualFile stopAt) {
    if (myCancelled || ourCancellingCondition != null && ourCancellingCondition.fun(stopAt)) {
      forceMarkDirty(stopAt);
      while (!myRefreshQueue.isEmpty()) {
        NewVirtualFile next = myRefreshQueue.pullFirst();
        forceMarkDirty(next);
      }
      throw new RefreshCancelledException();
    }
  }

  private static void forceMarkDirty(@NotNull NewVirtualFile file) {
    file.markClean();  // otherwise consequent markDirty() won't have any effect
    file.markDirty();
  }

  private static Function<? super VirtualFile, Boolean> ourCancellingCondition;

  @TestOnly
  static void setCancellingCondition(@Nullable Function<? super VirtualFile, Boolean> condition) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ourCancellingCondition = condition;
  }

  private class RefreshingFileVisitor extends SimpleFileVisitor<Path> {
    private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
    private final Map<String, VirtualFile> myPersistentChildren;
    private final Set<String> myChildrenWeAreInterested; // null - no limit

    private final VirtualFile myFileOrDir;
    private final PersistentFS myPersistence;
    private final NewVirtualFileSystem myFs;

    RefreshingFileVisitor(@NotNull VirtualFile fileOrDir,
                          @NotNull PersistentFS persistence,
                          @NotNull NewVirtualFileSystem fs,
                          @Nullable("null means all") Collection<String> childrenToRefresh,
                          @NotNull Collection<VirtualFile> existingPersistentChildren,
                          @NotNull TObjectHashingStrategy<String> strategy) {
      myFileOrDir = fileOrDir;
      myPersistence = persistence;
      myFs = fs;
      myPersistentChildren = new THashMap<>(existingPersistentChildren.size(), strategy);
      myChildrenWeAreInterested = childrenToRefresh != null ? new THashSet<>(childrenToRefresh, strategy) : null;

      for (VirtualFile child : existingPersistentChildren) {
        String name = child.getName();
        myPersistentChildren.put(name, child);
        if (myChildrenWeAreInterested != null) myChildrenWeAreInterested.add(name);
      }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      String name = file.getName(file.getNameCount() - 1).toString();

      if (acceptsFileName(name)) {
        NewVirtualFile child = (NewVirtualFile)myPersistentChildren.remove(name);
        boolean directory = attrs.isDirectory();

        if (child == null) { // new file is created
          VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();

          String symlinkTarget = attrs.isSymbolicLink() ? file.toRealPath().toString() : null;
          myHelper.scheduleCreation(parent, name, convert(file, attrs), isEmptyDir(file, attrs), symlinkTarget);
          return FileVisitResult.CONTINUE;
        }

        checkCancelled(child);

        if (!child.isDirty()) {
          return FileVisitResult.CONTINUE;
        }

        boolean oldIsDirectory = child.isDirectory();
        boolean oldIsSymlink = child.is(VFileProperty.SYMLINK);
        boolean oldIsSpecial = child.is(VFileProperty.SPECIAL);

        boolean isSpecial = attrs.isOther();
        boolean isLink = attrs.isSymbolicLink();

        if (isSpecial && directory && SystemInfo.isWindows) {
          // Windows junction is special directory, handle it as symlink
          isSpecial = false;
          isLink = true;
        }

        if (oldIsDirectory != directory ||
            oldIsSymlink != isLink ||
            oldIsSpecial != isSpecial) { // symlink or directory or special changed
          myHelper.scheduleDeletion(child);
          VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();
          String symlinkTarget = isLink ? file.toRealPath().toString() : null;
          myHelper.scheduleCreation(parent, child.getName(), convert(file, attrs), isEmptyDir(file, attrs), symlinkTarget);
          // ignore everything else
          child.markClean();
          return FileVisitResult.CONTINUE;
        }

        String currentName = child.getName();
        if (!currentName.equals(name)) {
          myHelper.scheduleAttributeChange(child, VirtualFile.PROP_NAME, currentName, name);
        }

        if (!directory) {
          myHelper.checkContentChanged(child, myPersistence.getTimeStamp(child), attrs.lastModifiedTime().toMillis(),
                                       myPersistence.getLastRecordedLength(child), attrs.size());
        }
        else {
          if (myIsRecursive) {
            myRefreshQueue.addLast(child);
          }
        }

        boolean currentWritable = myPersistence.isWritable(child);
        boolean isWritable;

        if (attrs instanceof DosFileAttributes) {
          DosFileAttributes dosFileAttributes = (DosFileAttributes)attrs;
          isWritable = directory || !dosFileAttributes.isReadOnly();
        }
        else if (attrs instanceof PosixFileAttributes) {
          isWritable = ((PosixFileAttributes)attrs).permissions().contains(PosixFilePermission.OWNER_WRITE);
        }
        else {
          isWritable = file.toFile().canWrite();
        }

        myHelper.checkWritableAttributeChange(child, currentWritable, isWritable);

        if (attrs instanceof DosFileAttributes) {
          myHelper.checkHiddenAttributeChange(child, child.is(VFileProperty.HIDDEN), ((DosFileAttributes)attrs).isHidden());
        }

        if (isLink) {
          myHelper.checkSymbolicLinkChange(child, child.getCanonicalPath(), myFs.resolveSymLink(child));
        }
        if (!child.isDirectory()) child.markClean();
      }
      return FileVisitResult.CONTINUE;
    }

    boolean acceptsFileName(String name) {
      return !VfsUtil.isBadName(name);
    }

    public void visit(@NotNull VirtualFile fileOrDir) {
      long started = System.nanoTime();

      try {
        Path path = Paths.get(fileOrDir.getPath());
        if (fileOrDir.isDirectory()) {
          if (myChildrenWeAreInterested == null) {
            Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), 1, this);
          }
          else {
            for (String child : myChildrenWeAreInterested) {
              try {
                Path subPath = path.resolve(child).toRealPath(LinkOption.NOFOLLOW_LINKS);
                BasicFileAttributes attributes = Files.readAttributes(subPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                visitFile(subPath, attributes);
              }
              catch (IOException ignore) {
              }
            }
          }
        }
        else {
          visitFile(path.toRealPath(LinkOption.NOFOLLOW_LINKS),
                    Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        }
      }
      catch (AccessDeniedException | NoSuchFileException ignore) {
      }
      catch (IOException ex) {
        LOG.error(ex);
      }

      int requests = myRequests.incrementAndGet();
      long l = myTime.addAndGet(System.nanoTime() - started);

      if (requests % 1000 == 0) {
        System.out.println("refresh:" + myRequests + " for " + TimeUnit.NANOSECONDS.toMillis(l) + "ms");
      }
    }

    @NotNull
    VfsEventGenerationHelper getHelper() {
      if (!myPersistentChildren.isEmpty()) {
        for (VirtualFile child : myPersistentChildren.values()) {
          myHelper.scheduleDeletion(child);
        }
        myPersistentChildren.clear();
      }

      return myHelper;
    }
  }

  private static boolean isEmptyDir(Path path, BasicFileAttributes a) {
    return a.isDirectory() && !LocalFileSystemBase.hasChildren(path);
  }

  private static FileAttributes convert(Path path, BasicFileAttributes a) throws IOException {
    boolean isSymlink = a.isSymbolicLink() || SystemInfo.isWindows && a.isOther() && a.isDirectory();

    if (isSymlink) {
      Class<? extends BasicFileAttributes> schema = SystemInfo.isWindows ? DosFileAttributes.class : PosixFileAttributes.class;
      try {
        a = Files.readAttributes(path, schema);
      }
      catch (NoSuchFileException | AccessDeniedException e) {
        return FileAttributes.BROKEN_SYMLINK;
      }
    }

    long lastModified = a.lastModifiedTime().toMillis();
    if (SystemInfo.isWindows) {
      boolean hidden = path.getParent() != null && ((DosFileAttributes)a).isHidden();
      boolean writable = a.isDirectory() || !((DosFileAttributes)a).isReadOnly();
      return new FileAttributes(a.isDirectory(), a.isOther(), isSymlink, hidden, a.size(), lastModified, writable);
    }
    else {
      boolean writable = Files.isWritable(path);
      return new FileAttributes(a.isDirectory(), a.isOther(), isSymlink, false, a.size(), lastModified, writable);
    }
  }
}