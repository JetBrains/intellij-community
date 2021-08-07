// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.newvfs.VfsEventGenerationHelper.LOG;

final class LocalFileSystemRefreshWorker {
  private final boolean myIsRecursive;
  private final NewVirtualFile myRefreshRoot;
  private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
  private volatile boolean myCancelled;

  LocalFileSystemRefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshRoot = refreshRoot;
  }

  @NotNull
  List<VFileEvent> getEvents() {
    return myHelper.getEvents();
  }

  void cancel() {
    myCancelled = true;
  }

  void scan() {
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

    RefreshContext context = createRefreshContext(fs, PersistentFS.getInstance());
    context.submitRefreshRequest(() -> processFile(root, context));
    context.waitForRefreshToFinish();
  }

  private @NotNull RefreshContext createRefreshContext(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistentFS) {
    int parallelism = Registry.intValue("vfs.use.nio-based.local.refresh.worker.parallelism", Runtime.getRuntime().availableProcessors() - 1);
    if (myIsRecursive && parallelism > 0 && !ApplicationManager.getApplication().isDispatchThread()) {
      return new ConcurrentRefreshContext(fs, persistentFS, parallelism);
    }
    return new SequentialRefreshContext(fs, persistentFS);
  }

  private void processFile(@NotNull NewVirtualFile file, @NotNull RefreshContext refreshContext) {
    if (!VfsEventGenerationHelper.checkDirty(file) || isCancelled(file, refreshContext)) {
      return;
    }

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

    if (isCancelled(file, refreshContext)) {
      return;
    }

    if (myIsRecursive || !file.isDirectory()) {
      file.markClean();
    }
  }

  private abstract static class RefreshContext {
    final NewVirtualFileSystem fs;
    final PersistentFS persistence;
    final Queue<NewVirtualFile> filesToBecomeDirty = new LinkedBlockingQueue<>();

    RefreshContext(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistence) {
      this.fs = fs;
      this.persistence = persistence;
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
    RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(file, refreshContext, null, Collections.singletonList(file));
    refreshingFileVisitor.visit(file);
    addAllEventsFrom(refreshingFileVisitor);
  }

  private void addAllEventsFrom(@NotNull RefreshingFileVisitor refreshingFileVisitor) {
    synchronized (myHelper) {
      myHelper.addAllEventsFrom(refreshingFileVisitor.getHelper());
    }
  }

  private void fullDirRefresh(@NotNull VirtualDirectoryImpl dir, @NotNull RefreshContext refreshContext) {
    while (true) {
      Pair<List<String>, List<VirtualFile>> snapshot = getDirectorySnapshot(dir);
      if (snapshot == null) return;

      RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(dir, refreshContext, null, snapshot.second);
      refreshingFileVisitor.visit(dir);
      if (myCancelled) {
        addAllEventsFrom(refreshingFileVisitor);
        break;
      }

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (ApplicationManager.getApplication().isDisposed()) {
          return true;
        }
        if (areChildrenOrNamesChanged(dir, snapshot.first, snapshot.second)) {
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

  @Nullable
  static Pair<List<String>, List<VirtualFile>> getDirectorySnapshot(@NotNull VirtualDirectoryImpl dir) {
    return ReadAction.compute(() -> {
      if (ApplicationManager.getApplication().isDisposed()) {
        return null;
      }
      VirtualFile[] children = dir.getChildren();
      return Pair.create(getNames(children), Arrays.asList(children));
    });
  }

  static boolean areChildrenOrNamesChanged(@NotNull VirtualDirectoryImpl dir, @NotNull List<String> names, @NotNull List<? extends VirtualFile> children) {
    VirtualFile[] currentChildren = dir.getChildren();
    return !children.equals(Arrays.asList(currentChildren)) || !names.equals(getNames(currentChildren));
  }

  private static @NotNull List<String> getNames(VirtualFile @NotNull [] children) {
    return ContainerUtil.map(children, VirtualFile::getName);
  }

  private void partialDirRefresh(@NotNull VirtualDirectoryImpl dir, @NotNull RefreshContext refreshContext) {
    while (true) {
      // obtaining directory snapshot
      Pair<List<VirtualFile>, List<String>> result = ReadAction.compute(() -> new Pair<>(dir.getCachedChildren(), dir.getSuspiciousNames()));

      List<VirtualFile> cached = result.getFirst();
      List<String> wanted = result.getSecond();

      if (cached.isEmpty() && wanted.isEmpty()) return;
      RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(dir, refreshContext, wanted, cached);
      refreshingFileVisitor.visit(dir);
      if (myCancelled) {
        addAllEventsFrom(refreshingFileVisitor);
        break;
      }

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

  private boolean isCancelled(@NotNull NewVirtualFile stopAt, @NotNull RefreshContext refreshContext) {
    if (ourTestListener != null) {
      ourTestListener.accept(stopAt);
    }
    if (myCancelled) {
      refreshContext.filesToBecomeDirty.offer(stopAt);
      return true;
    }
    return false;
  }

  private void checkCancelled(@NotNull NewVirtualFile stopAt, @NotNull RefreshContext refreshContext) throws RefreshWorker.RefreshCancelledException {
    if (isCancelled(stopAt, refreshContext)) throw new RefreshWorker.RefreshCancelledException();
  }

  private static void forceMarkDirty(@NotNull NewVirtualFile file) {
    file.markClean();  // otherwise consequent markDirty() won't have any effect
    file.markDirty();
  }

  private static Consumer<? super VirtualFile> ourTestListener;

  @TestOnly
  static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    ourTestListener = testListener;
  }

  private static final class SequentialRefreshContext extends RefreshContext {
    private final Deque<Runnable> myRefreshRequests = new ArrayDeque<>(100);

    SequentialRefreshContext(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistentFS) {
      super(fs, persistentFS);
    }

    @Override
    void submitRefreshRequest(@NotNull Runnable request) {
      myRefreshRequests.addLast(request);
    }

    @Override
    void doWaitForRefreshToFinish() {
      Runnable runnable;
      while ((runnable = myRefreshRequests.pollFirst()) != null) {
        runnable.run();
      }
    }
  }

  private static final class ConcurrentRefreshContext extends RefreshContext {
    private final ExecutorService service;
    private final AtomicInteger tasksScheduled = new AtomicInteger();
    private final CountDownLatch refreshFinishedLatch = new CountDownLatch(1);

    ConcurrentRefreshContext(@NotNull NewVirtualFileSystem fs,
                             @NotNull PersistentFS persistentFS,
                             int parallelism) {
      super(fs, persistentFS);
      service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Refresh Worker", parallelism);
    }

    @Override
    void submitRefreshRequest(@NotNull Runnable action) {
      tasksScheduled.incrementAndGet();

      service.execute(() -> {
        try {
          action.run();
        }
        finally {
          if (tasksScheduled.decrementAndGet() == 0) {
            refreshFinishedLatch.countDown();
          }
        }
      });
    }

    @Override
    void doWaitForRefreshToFinish() {
      try {
        refreshFinishedLatch.await(1, TimeUnit.DAYS);
        service.shutdown();
      }
      catch (InterruptedException ignore) { }
    }
  }

  private final class RefreshingFileVisitor extends SimpleFileVisitor<Path> {
    private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
    private final Map<String, VirtualFile> myPersistentChildren;
    private final Set<String> myChildrenWeAreInterested; // null - no limit

    private final NewVirtualFile myFileOrDir;
    private final RefreshContext myRefreshContext;

    RefreshingFileVisitor(@NotNull NewVirtualFile fileOrDir,
                          @NotNull RefreshContext refreshContext,
                          @Nullable("null means all") Collection<String> childrenToRefresh,
                          @NotNull Collection<? extends VirtualFile> existingPersistentChildren) {
      myFileOrDir = fileOrDir;
      myRefreshContext = refreshContext;
      myPersistentChildren = CollectionFactory.createFilePathMap(existingPersistentChildren.size(), fileOrDir.isCaseSensitive());
      myChildrenWeAreInterested = childrenToRefresh == null ? null : CollectionFactory.createFilePathSet(childrenToRefresh, fileOrDir.isCaseSensitive());

      for (VirtualFile child : existingPersistentChildren) {
        String name = child.getName();
        myPersistentChildren.put(name, child);
        if (myChildrenWeAreInterested != null) {
          myChildrenWeAreInterested.add(name);
        }
      }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
      String name = file.getName(file.getNameCount() - 1).toString();

      if (!acceptsFileName(name)) {
        return FileVisitResult.CONTINUE;
      }

      NewVirtualFile child = (NewVirtualFile)myPersistentChildren.remove(name);
      boolean isDirectory = attributes.isDirectory();
      boolean isSpecial = attributes.isOther();
      boolean isLink = attributes.isSymbolicLink();

      if (isSpecial && isDirectory && SystemInfo.isWindows) {
        // Windows junction is a special directory, handle it as symlink
        isSpecial = false;
        isLink = true;
      }

      if (isLink) {
        try {
          attributes = Files.readAttributes(file, BasicFileAttributes.class);
        }
        catch (FileSystemException ignore) {
          attributes = BROKEN_SYMLINK_ATTRIBUTES;
        }
        isDirectory = attributes.isDirectory();
      }
      else if (myFileOrDir.is(VFileProperty.SYMLINK)) {
        try {
          attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (NoSuchFileException | AccessDeniedException ignore) {
          attributes = BROKEN_SYMLINK_ATTRIBUTES;
        }
        isLink = attributes.isSymbolicLink();
      }

      if (child == null) { // new file is created
        VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();

        String symLinkTarget = isLink ? FileUtil.toSystemIndependentName(file.toRealPath().toString()) : null;
        try {
          FileAttributes fa = toFileAttributesWithCaseInformation(file, attributes, isLink);
          myHelper.scheduleCreation(parent, name, fa, symLinkTarget, () -> checkCancelled(myFileOrDir, myRefreshContext));
        }
        catch (RefreshWorker.RefreshCancelledException e) {
          return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
      }

      if (isCancelled(child, myRefreshContext)) {
        return FileVisitResult.TERMINATE;
      }

      if (!child.isDirty()) {
        return FileVisitResult.CONTINUE;
      }

      boolean oldIsDirectory = child.isDirectory();
      boolean oldIsSymlink = child.is(VFileProperty.SYMLINK);
      boolean oldIsSpecial = child.is(VFileProperty.SPECIAL);

      if (oldIsDirectory != isDirectory ||
          oldIsSymlink != isLink ||
          oldIsSpecial != isSpecial) { // symlink or directory or special changed
        myHelper.scheduleDeletion(child);
        VirtualFile parent = myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent();
        String symLinkTarget = isLink ? FileUtil.toSystemIndependentName(file.toRealPath().toString()) : null;
        try {
          FileAttributes fa = toFileAttributesWithCaseInformation(file, attributes, isLink);
          myHelper.scheduleCreation(parent, child.getName(), fa, symLinkTarget, () -> checkCancelled(myFileOrDir, myRefreshContext));
        }
        catch (RefreshWorker.RefreshCancelledException e) {
          return FileVisitResult.TERMINATE;
        }
        // ignore everything else
        child.markClean();
        return FileVisitResult.CONTINUE;
      }

      String currentName = child.getName();
      if (!currentName.equals(name)) {
        myHelper.scheduleAttributeChange(child, VirtualFile.PROP_NAME, currentName, name);
      }

      if (!isDirectory) {
        myHelper.checkContentChanged(child,
                                     myRefreshContext.persistence.getTimeStamp(child), attributes.lastModifiedTime().toMillis(),
                                     myRefreshContext.persistence.getLastRecordedLength(child), attributes.size());
      }

      myHelper.checkWritableAttributeChange(child, myRefreshContext.persistence.isWritable(child), isWritable(file, attributes, isDirectory));

      if (attributes instanceof DosFileAttributes) {
        myHelper.checkHiddenAttributeChange(child, child.is(VFileProperty.HIDDEN), ((DosFileAttributes)attributes).isHidden());
      }

      if (isLink) {
        myHelper.checkSymbolicLinkChange(child, child.getCanonicalPath(), myRefreshContext.fs.resolveSymLink(child));
      }

      if (!child.isDirectory()) {
        child.markClean();
      }
      else if (myIsRecursive) {
        myRefreshContext.submitRefreshRequest(() -> processFile(child, myRefreshContext));
      }
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
            // Files.walkFileTree is more efficient than File.openDirectoryStream / readAttributes because former provides access to cached
            // file attributes of visited children, see usages of BasicFileAttributesHolder in FileTreeWalker.getAttributes
            EnumSet<FileVisitOption> options =
              fileOrDir.is(VFileProperty.SYMLINK) ? EnumSet.of(FileVisitOption.FOLLOW_LINKS) : EnumSet.noneOf(FileVisitOption.class);
            Files.walkFileTree(path, options, 1, this);
          }
          else {
            for (String child : myChildrenWeAreInterested) {
              try {
                Path subPath = fixCaseIfNeeded(path.resolve(child), fileOrDir);
                BasicFileAttributes attrs = Files.readAttributes(subPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                FileVisitResult result = visitFile(subPath, attrs);
                if (result == FileVisitResult.TERMINATE) break;
              }
              catch (IOException ignore) { }
            }
          }
        }
        else {
          Path file = fixCaseIfNeeded(path, fileOrDir);
          BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          visitFile(file, attrs);
        }
      }
      catch (AccessDeniedException | NoSuchFileException ignore) { }
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

  @NotNull
  private static Path fixCaseIfNeeded(@NotNull Path path, @NotNull VirtualFile file) throws IOException {
    if (file.isCaseSensitive()) return path;
    // Mac: toRealPath() will return the current file's name w.r.t. case
    // Win: toRealPath(LinkOption.NOFOLLOW_LINKS) will return the current file's name w.r.t. case
    return file.is(VFileProperty.SYMLINK) ? path.toRealPath(LinkOption.NOFOLLOW_LINKS) : path.toRealPath();
  }

  private static boolean isWritable(@NotNull Path file, @NotNull BasicFileAttributes a, boolean directory) {
    boolean isWritable;

    if (a instanceof DosFileAttributes) {
      DosFileAttributes dosFileAttributes = (DosFileAttributes)a;
      isWritable = directory || !dosFileAttributes.isReadOnly();
    }
    else if (a instanceof PosixFileAttributes) {
      isWritable = ((PosixFileAttributes)a).permissions().contains(PosixFilePermission.OWNER_WRITE);
    }
    else {
      isWritable = file.toFile().canWrite();
    }
    return isWritable;
  }

  @NotNull
  private static FileAttributes toFileAttributesWithCaseInformation(@NotNull Path path, @NotNull BasicFileAttributes a, boolean isSymlink) {
    if (isSymlink && a == BROKEN_SYMLINK_ATTRIBUTES) {
      return FileAttributes.BROKEN_SYMLINK;
    }

    long lastModified = a.lastModifiedTime().toMillis();
    boolean writable = isWritable(path, a, a.isDirectory());
    boolean isHidden;
    if (SystemInfo.isWindows) {
      isHidden = path.getParent() != null && ((DosFileAttributes)a).isHidden();
    }
    else {
      isHidden = false;
    }
    return new FileAttributes(a.isDirectory(), a.isOther(), isSymlink, isHidden, a.size(), lastModified, writable);
  }

  private static final BasicFileAttributes BROKEN_SYMLINK_ATTRIBUTES = new BasicFileAttributes() {
    private final FileTime myFileTime = FileTime.fromMillis(0);
    @Override public FileTime lastModifiedTime() { return myFileTime; }
    @Override public FileTime lastAccessTime() { return myFileTime; }
    @Override public FileTime creationTime() { return myFileTime; }
    @Override public boolean isRegularFile() { return false; }
    @Override public boolean isDirectory() { return false; }
    @Override public boolean isSymbolicLink() { return true; }
    @Override public boolean isOther() { return false; }
    @Override public long size() { return 0; }
    @Override public Object fileKey() { return this; }
  };
}