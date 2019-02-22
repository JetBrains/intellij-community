// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.registry.Registry;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.vfs.newvfs.persistent.VfsEventGenerationHelper.LOG;

class LocalFileSystemRefreshWorker {
  private final boolean myIsRecursive;
  private final NewVirtualFile myRefreshRoot;
  private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
  private volatile boolean myCancelled;

  LocalFileSystemRefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshRoot = refreshRoot;
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myHelper.getEvents();
  }

  public void cancel() {
    myCancelled = true;
  }

  public void scan() {
    NewVirtualFile root = myRefreshRoot;
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

    RefreshContext context = createRefreshContext(fs, PersistentFS.getInstance(), FilePathHashingStrategy.create(fs.isCaseSensitive()));
    context.submitRefreshRequest(() -> processFile(root, context));
    context.waitForRefreshToFinish();
  }

  @NotNull
  private RefreshContext createRefreshContext(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistentFS, @NotNull TObjectHashingStrategy<String> strategy) {
    int parallelism = Registry.intValue("vfs.use.nio-based.local.refresh.worker.parallelism", Runtime.getRuntime().availableProcessors() - 1);
    
    if (myIsRecursive && parallelism > 0) {
      final ForkJoinPool pool = new ForkJoinPool(parallelism);

      return new RefreshContext(fs, persistentFS, strategy) {
        @Override
        void submitRefreshRequest(@NotNull Runnable action) {
          pool.submit(action);
        }

        @Override
        void doWaitForRefreshToFinish() {
          pool.awaitQuiescence(1, TimeUnit.DAYS);
          pool.shutdown();
        }
      };
    }

    return new RefreshContext(fs, persistentFS, strategy) {
      final Queue<Runnable> myRefreshRequests = new Queue<>(100);
      
      @Override
      void submitRefreshRequest(@NotNull Runnable request) {
        myRefreshRequests.addLast(request);
      }

      @Override
      void doWaitForRefreshToFinish() {
        while(!myRefreshRequests.isEmpty()) {
          Runnable request = myRefreshRequests.pullFirst();
          request.run();
        }
      }
    };
  }

  private void processFile(@NotNull NewVirtualFile file, @NotNull RefreshContext refreshContext) {
    if (!myHelper.checkDirty(file)) {
      return;
    }

    if(checkCancelled(file, refreshContext)) return;

    if (file.isDirectory()) {
      boolean fullSync = ((VirtualDirectoryImpl)file).allChildrenLoaded();
      if (fullSync) {
        fullDirRefresh((VirtualDirectoryImpl)file, refreshContext);
      }
      else {
        partialDirRefresh((VirtualDirectoryImpl)file, refreshContext);
      }
    }
    else {
      refreshFile(file, refreshContext);
    }

    if(checkCancelled(file, refreshContext)) return;
    
    if (myIsRecursive || !file.isDirectory()) {
      file.markClean();
    }
  }
  
  private abstract static class RefreshContext {
    final NewVirtualFileSystem fs;
    final PersistentFS persistence;
    final TObjectHashingStrategy<String> strategy;
    final BlockingQueue<NewVirtualFile> filesToBecomeDirty = new LinkedBlockingQueue<>();

    RefreshContext(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistence, @NotNull TObjectHashingStrategy<String> strategy) {
      this.fs = fs;
      this.persistence = persistence;
      this.strategy = strategy;
    }

    abstract void submitRefreshRequest(@NotNull Runnable action);
    abstract void doWaitForRefreshToFinish();

    final void waitForRefreshToFinish() {
      doWaitForRefreshToFinish();

      for (NewVirtualFile file : filesToBecomeDirty) {
        forceMarkDirty(file);
      }
    }
  }

  private void refreshFile(@NotNull NewVirtualFile file, @NotNull RefreshContext refreshContext) {
    RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(file, refreshContext, null,
                                                                            Collections.singletonList(file));

    refreshingFileVisitor.visit(file);
    addAllEventsFrom(refreshingFileVisitor);
  }

  private void addAllEventsFrom(RefreshingFileVisitor refreshingFileVisitor) {
    synchronized (myHelper) {
      myHelper.addAllEventsFrom(refreshingFileVisitor.getHelper());
    }
  }

  private void fullDirRefresh(@NotNull VirtualDirectoryImpl dir, @NotNull RefreshContext refreshContext) {
    while (true) {
      // obtaining directory snapshot
      Pair<String[], VirtualFile[]> result = getDirectorySnapshot(refreshContext.persistence, dir);
      if (result == null) return;
      String[] persistedNames = result.getFirst();
      VirtualFile[] children = result.getSecond();

      RefreshingFileVisitor refreshingFileVisitor =
        new RefreshingFileVisitor(dir, refreshContext, null, Arrays.asList(children));

      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (ApplicationManager.getApplication().isDisposed()) {
          return true;
        }
        if (!Arrays.equals(persistedNames, refreshContext.persistence.list(dir)) || !Arrays.equals(children, dir.getChildren())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        addAllEventsFrom(refreshingFileVisitor);
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

  private void partialDirRefresh(@NotNull VirtualDirectoryImpl dir, @NotNull RefreshContext refreshContext) {
    while (true) {
      // obtaining directory snapshot
      Pair<List<VirtualFile>, List<String>> result =
        ReadAction.compute(() -> Pair.create(dir.getCachedChildren(), dir.getSuspiciousNames()));

      List<VirtualFile> cached = result.getFirst();
      List<String> wanted = result.getSecond();

      if (cached.isEmpty() && wanted.isEmpty()) return;
      RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(dir, refreshContext, wanted, cached);
      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (!cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        addAllEventsFrom(refreshingFileVisitor);

        return true;
      });
      if (hasEvents) {
        break;
      }
    }
  }

  private boolean checkCancelled(@NotNull NewVirtualFile stopAt, @NotNull RefreshContext refreshContext) {
    boolean myRequestedCancel = false;
    if (myCancelled || (myRequestedCancel = ourCancellingCondition != null && ourCancellingCondition.fun(stopAt))) {
      if (myRequestedCancel) myCancelled = true;
      refreshContext.filesToBecomeDirty.offer(stopAt);
      return true;
    }
    return false;
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
    private final RefreshContext myRefreshContext;

    RefreshingFileVisitor(@NotNull VirtualFile fileOrDir,
                          @NotNull RefreshContext refreshContext,
                          @Nullable("null means all") Collection<String> childrenToRefresh,
                          @NotNull Collection<VirtualFile> existingPersistentChildren) {
      myFileOrDir = fileOrDir;
      myRefreshContext = refreshContext;
      myPersistentChildren = new THashMap<>(existingPersistentChildren.size(), refreshContext.strategy);
      myChildrenWeAreInterested = childrenToRefresh != null ? new THashSet<>(childrenToRefresh, refreshContext.strategy) : null;

      for (VirtualFile child : existingPersistentChildren) {
        String name = child.getName();
        myPersistentChildren.put(name, child);
        if (myChildrenWeAreInterested != null) myChildrenWeAreInterested.add(name);
      }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      String name = file.getName(file.getNameCount() - 1).toString();

      if (!acceptsFileName(name)) {
        return FileVisitResult.CONTINUE;
      }
      NewVirtualFile child = (NewVirtualFile)myPersistentChildren.remove(name);
      boolean isDirectory = attrs.isDirectory();

      if (child == null) { // new file is created
        VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();

        String symlinkTarget = attrs.isSymbolicLink() ? file.toRealPath().toString() : null;
        myHelper.scheduleCreation(parent, name, toFileAttributes(file, attrs), isEmptyDir(file, attrs), symlinkTarget);
        return FileVisitResult.CONTINUE;
      }

        if(checkCancelled(child, myRefreshContext)) {
          return FileVisitResult.TERMINATE;
        }

      if (!child.isDirty()) {
        return FileVisitResult.CONTINUE;
      }

      boolean oldIsDirectory = child.isDirectory();
      boolean oldIsSymlink = child.is(VFileProperty.SYMLINK);
      boolean oldIsSpecial = child.is(VFileProperty.SPECIAL);

      boolean isSpecial = attrs.isOther();
      boolean isLink = attrs.isSymbolicLink();

      if (isSpecial && isDirectory && SystemInfo.isWindows) {
        // Windows junction is special directory, handle it as symlink
        isSpecial = false;
        isLink = true;
      }

      if (oldIsDirectory != isDirectory ||
          oldIsSymlink != isLink ||
          oldIsSpecial != isSpecial) { // symlink or directory or special changed
        myHelper.scheduleDeletion(child);
        VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();
        String symlinkTarget = isLink ? file.toRealPath().toString() : null;
        myHelper.scheduleCreation(parent, child.getName(), toFileAttributes(file, attrs), isEmptyDir(file, attrs), symlinkTarget);
        // ignore everything else
        child.markClean();
        return FileVisitResult.CONTINUE;
      }

      String currentName = child.getName();
      if (!currentName.equals(name)) {
        myHelper.scheduleAttributeChange(child, VirtualFile.PROP_NAME, currentName, name);
      }

      if (!isDirectory) {
        myHelper.checkContentChanged(child, myRefreshContext.persistence.getTimeStamp(child), attrs.lastModifiedTime().toMillis(),
                                     myRefreshContext.persistence.getLastRecordedLength(child), attrs.size());
      }
      else {
        if (myIsRecursive) {
          myRefreshContext.submitRefreshRequest(() -> processFile(child, myRefreshContext));
        }
      }

      boolean currentWritable = myRefreshContext.persistence.isWritable(child);
      boolean isWritable;

      if (attrs instanceof DosFileAttributes) {
        DosFileAttributes dosFileAttributes = (DosFileAttributes)attrs;
        isWritable = isDirectory || !dosFileAttributes.isReadOnly();
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
        myHelper.checkSymbolicLinkChange(child, child.getCanonicalPath(), myRefreshContext.fs.resolveSymLink(child));
      }
      if (!child.isDirectory()) child.markClean();
      return FileVisitResult.CONTINUE;
    }

    boolean acceptsFileName(@NotNull String name) {
      return !VfsUtil.isBadName(name);
    }

    void visit(@NotNull VirtualFile fileOrDir) {
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
    }

    @NotNull
    VfsEventGenerationHelper getHelper() {
      if (!myPersistentChildren.isEmpty()) {
        if (!myCancelled) {
          for (VirtualFile child : myPersistentChildren.values()) {
            myHelper.scheduleDeletion(child);
          }
        }
        myPersistentChildren.clear();
      }

      return myHelper;
    }
  }

  private static boolean isEmptyDir(@NotNull Path path, @NotNull BasicFileAttributes a) {
    return a.isDirectory() && !LocalFileSystemBase.hasChildren(path);
  }

  @NotNull
  private static FileAttributes toFileAttributes(@NotNull Path path, @NotNull BasicFileAttributes a) throws IOException {
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