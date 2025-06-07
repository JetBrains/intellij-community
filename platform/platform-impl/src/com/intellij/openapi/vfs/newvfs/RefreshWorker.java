// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.MathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class RefreshWorker {
  private static final Logger LOG = Logger.getInstance(RefreshWorker.class);

  private static final int ourParallelism =
    MathUtil.clamp(Registry.intValue("vfs.refresh.worker.parallelism", 6), 1, Runtime.getRuntime().availableProcessors());
  private static final Executor ourExecutor = ExecutorsKt.asExecutor(
    Dispatchers.getIO().limitedParallelism(ourParallelism, "RefreshWorkerDispatcher")
  );

  private final boolean myIsRecursive;
  private final boolean myParallel;
  private final Set<NewVirtualFile> myRoots;
  private final Queue<NewVirtualFile> myRefreshQueue;
  private final Semaphore mySemaphore;
  private final Object myRequestor;
  private final PersistentFS myPersistence = PersistentFS.getInstance();
  private final FSRecordsImpl myPersistencePeer = ((PersistentFSImpl)myPersistence).peer();
  private volatile boolean myCancelled;

  private final AtomicInteger myFullScans = new AtomicInteger(), myPartialScans = new AtomicInteger(), myProcessed = new AtomicInteger();
  private final AtomicLong myVfsTime = new AtomicLong(), myIoTime = new AtomicLong();

  RefreshWorker(Collection<NewVirtualFile> refreshRoots, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myParallel = isRecursive && ourParallelism > 1 && !ApplicationManager.getApplication().isWriteIntentLockAcquired();
    myRoots = new HashSet<>(refreshRoots);
    myRefreshQueue = new LinkedBlockingQueue<>(refreshRoots);
    mySemaphore = new Semaphore(refreshRoots.size());
    myRequestor = VFileEvent.REFRESH_REQUESTOR;
  }

  void cancel() {
    myCancelled = true;
  }

  List<VFileEvent> scan() {
    var t = System.nanoTime();
    try {
      var events = new ArrayList<VFileEvent>();
      if (!myParallel) {
        singleThreadScan(events);
      }
      else {
        parallelScan(events);
      }
      return events;
    }
    finally {
      t = NANOSECONDS.toMillis(System.nanoTime() - t);
      var retries = myFullScans.get() + myPartialScans.get() - myProcessed.get();
      VfsUsageCollector.logRefreshScan(myFullScans.get(), myPartialScans.get(), retries, t,
                                       NANOSECONDS.toMillis(myVfsTime.get()), NANOSECONDS.toMillis(myIoTime.get()));
    }
  }

  private void singleThreadScan(List<VFileEvent> events) {
    try {
      processQueue(events);
    }
    catch (RefreshCancelledException e) {
      LOG.trace("refresh cancelled [1T]");
    }
  }

  private void parallelScan(List<VFileEvent> events) {
    var futures = new ArrayList<CompletableFuture<List<VFileEvent>>>(ourParallelism);

    for (var i = 0; i < ourParallelism; i++) {
      futures.add(CompletableFuture.supplyAsync(() -> {
        var threadEvents = new ArrayList<VFileEvent>();
        try {
          processQueue(threadEvents);
        }
        catch (RefreshCancelledException ignored) { }
        catch (CancellationException e) {
          myCancelled = true;
        }
        catch (Throwable t) {
          LOG.error(t);
          myCancelled = true;
        }
        return threadEvents;
      }, ourExecutor));
    }

    for (var future : futures) {
      try {
        events.addAll(future.get());
      }
      catch (InterruptedException ignored) { }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }

    if (myCancelled) {
      LOG.trace("refresh cancelled [MT]");
    }
  }

  private void processQueue(List<VFileEvent> events) throws RefreshCancelledException {
    nextDir:
    while (!mySemaphore.isUp()) {
      var file = myRefreshQueue.poll();
      if (file == null) {
        TimeoutUtil.sleep(1);
        continue;
      }

      var fs = file.getFileSystem();

      try {
        if (myRoots.contains(file)) {
          var attributes = computeAttributesForFile(fs, file);
          if (attributes == null) {
            scheduleDeletion(events, file);
            file.markClean();
            continue;
          }

          checkAndScheduleChildRefresh(events, fs, file.getParent(), file, attributes, false);

          if (!file.isDirty() || !file.isDirectory()) {
            continue;
          }
        }

        var dir = (VirtualDirectoryImpl)file;
        var mark = events.size();

        while (true) {
          checkCancelled(dir);
          var fullSync = dir.allChildrenLoaded();
          (fullSync ? myFullScans : myPartialScans).incrementAndGet();
          try {
            var success = fullSync ? fullDirRefresh(events, fs, dir) : partialDirRefresh(events, fs, dir);
            if (success) break;
            events.subList(mark, events.size()).clear();
            if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          }
          catch (InvalidVirtualFileAccessException e) {
            events.subList(mark, events.size()).clear();
            continue nextDir;
          }
          finally {
            clearFsCache(fs);
          }
        }
        myProcessed.incrementAndGet();

        if (myIsRecursive) {
          dir.markClean();
        }
      }
      finally {
        mySemaphore.up();
      }
    }
  }

  private boolean fullDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<VirtualFile[], List<String>> snapshot = ReadAction.compute(() -> {
      VirtualFile[] children = dir.getChildren();
      return new Pair<>(children, getNames(children));
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    VirtualFile[] vfsChildren = snapshot.first;
    List<String> vfsNames = snapshot.second;

    Map<String, FileAttributes> dirList;
    t = System.nanoTime();
    if (fs instanceof BatchingFileSystem) {
      Map<String, FileAttributes> rawDirList = ((BatchingFileSystem)fs).listWithAttributes(dir, null);
      dirList = adjustCaseSensitivity(rawDirList, dir.isCaseSensitive());
    }
    else {
      dirList = new HashMap<>();
      for (String name : fs instanceof LocalFileSystemImpl ? computeListWithCaching((LocalFileSystemImpl)fs, dir, null) : fs.list(dir)) {
        dirList.put(name, null);
      }
    }
    myIoTime.addAndGet(System.nanoTime() - t);

    Set<String> newNames = new HashSet<>(dirList.keySet());
    vfsNames.forEach(newNames::remove);

    Set<String> deletedNames = new HashSet<>(vfsNames);
    dirList.keySet().forEach(deletedNames::remove);

    ObjectOpenCustomHashSet<String> actualNames =
      dir.isCaseSensitive() ? null : (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(dirList.keySet(), false);
    if (LOG.isTraceEnabled()) {
      LOG.trace("current=" + vfsNames + " +" + newNames + " -" + deletedNames);
    }

    List<ChildInfo> newKids = newNames.isEmpty() && deletedNames.isEmpty() ? List.of() : new ArrayList<>(newNames.size());
    for (String newName : newNames) {
      if (VfsUtil.isBadName(newName)) continue;
      FakeVirtualFile child = new FakeVirtualFile(dir, newName);
      FileAttributes attributes = getAttributes(fs, dirList, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, false));
      }
    }

    List<Pair<VirtualFile, FileAttributes>> existingMap = new ArrayList<>(vfsChildren.length - deletedNames.size());
    for (VirtualFile child : vfsChildren) {
      if (!deletedNames.contains(child.getName())) {
        existingMap.add(new Pair<>(child, getAttributes(fs, dirList, child)));
      }
    }

    clearFsCache(fs);
    checkCancelled(dir);
    if (isDirectoryChanged(dir, vfsChildren, vfsNames)) {
      return false;
    }

    generateDeleteEvents(events, dir, deletedNames, actualNames, newKids);

    generateCreateEvents(events, dir, newKids);

    generateUpdateEvents(events, fs, dir, actualNames, existingMap);

    checkCancelled(dir);
    return !isDirectoryChanged(dir, vfsChildren, vfsNames);
  }

  private static @Unmodifiable List<String> getNames(VirtualFile[] children) {
    return ContainerUtil.map(children, VirtualFile::getName);
  }

  private boolean isDirectoryChanged(VirtualDirectoryImpl dir, VirtualFile[] children, List<String> names) {
    var t = System.nanoTime();
    var changed = ReadAction.compute(() -> {
      VirtualFile[] currentChildren = dir.getChildren();
      return !Arrays.equals(children, currentChildren) || !names.equals(getNames(currentChildren));
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  private boolean partialDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<List<VirtualFile>, List<String>> snapshot = ReadAction.compute(() -> new Pair<>(dir.getCachedChildren(), dir.getSuspiciousNames()));
    myVfsTime.addAndGet(System.nanoTime() - t);
    List<VirtualFile> cached = snapshot.first;
    List<String> wanted = snapshot.second;

    Set<String> names = CollectionFactory.createFilePathSet(wanted, dir.isCaseSensitive());
    for (VirtualFile file : cached) names.add(file.getName());

    Map<String, FileAttributes> dirList = null;
    if (fs instanceof BatchingFileSystem) {
      t = System.nanoTime();
      Map<String, FileAttributes> rawDirList = ((BatchingFileSystem)fs).listWithAttributes(dir, names);
      myIoTime.addAndGet(System.nanoTime() - t);
      dirList = adjustCaseSensitivity(rawDirList, dir.isCaseSensitive());
    }

    ObjectOpenCustomHashSet<String> actualNames;
    if (dir.isCaseSensitive() || cached.isEmpty()) {
      actualNames = null;
    }
    else if (dirList != null) {
      actualNames = (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(dirList.keySet(), false);
    }
    else {
      t = System.nanoTime();
      String[] rawList = fs instanceof LocalFileSystemImpl ? computeListWithCaching((LocalFileSystemImpl)fs, dir, names) : fs.list(dir);
      actualNames = (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(rawList, false);
      myIoTime.addAndGet(System.nanoTime() - t);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("cached=" + cached + " actual=" + actualNames + " suspicious=" + wanted);
    }

    List<ChildInfo> newKids = wanted.isEmpty() ? List.of() : new ArrayList<>(wanted.size());
    for (String newName : wanted) {
      if (VfsUtil.isBadName(newName)) continue;
      FakeVirtualFile child = new FakeVirtualFile(dir, newName);
      FileAttributes attributes = getAttributes(fs, dirList, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, true));
      }
    }

    List<Pair<VirtualFile, FileAttributes>> existingMap = cached.isEmpty() ? List.of() : new ArrayList<>(cached.size());
    for (VirtualFile child : cached) {
      existingMap.add(new Pair<>(child, getAttributes(fs, dirList, child)));
    }

    clearFsCache(fs);
    checkCancelled(dir);
    if (isDirectoryChanged(dir, cached, wanted)) {
      return false;
    }

    generateCreateEvents(events, dir, newKids);

    generateUpdateEvents(events, fs, dir, actualNames, existingMap);

    checkCancelled(dir);
    return !isDirectoryChanged(dir, cached, wanted);
  }

  private boolean isDirectoryChanged(VirtualDirectoryImpl dir, List<VirtualFile> cached, List<String> wanted) {
    var t = System.nanoTime();
    var changed = ReadAction.compute(() -> !cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames()));
    myVfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  private static Map<String, FileAttributes> adjustCaseSensitivity(Map<String, FileAttributes> rawDirList, boolean cs) {
    if (cs) {
      return rawDirList;
    }
    else {
      Map<String, FileAttributes> filtered = CollectionFactory.createFilePathMap(rawDirList.size(), false);
      filtered.putAll(rawDirList);
      return filtered;
    }
  }

  private @Nullable FileAttributes getAttributes(NewVirtualFileSystem fs, @Nullable Map<String, FileAttributes> dirList, VirtualFile child) {
    FileAttributes attributes = null;
    if (dirList != null) {
      attributes = dirList.get(child.getName());
    }
    if (attributes == null && !(fs instanceof BatchingFileSystem)) {
      var t = System.nanoTime();
      attributes = computeAttributesForFile(fs, child);
      myIoTime.addAndGet(System.nanoTime() - t);
    }
    return attributes;
  }

  /**
   * If attributes are computed in a cancellable context, then single-thread refresh gets a performance degradation.
   * The reason is com.intellij.openapi.vfs.DiskQueryRelay#accessDiskWithCheckCanceled(java.lang.Object),
   * which starts constant exchanging messages with an IO thread.
   * The non-cancellable section here is merely a reification of the existing implicit assumption on cancellability,
   * so it does not make anything worse.
   * In the future, it should be removed in favor of non-blocking or suspending IO.
   */
  private static @Nullable FileAttributes computeAttributesForFile(NewVirtualFileSystem fs, VirtualFile file) {
    return Cancellation.computeInNonCancelableSection(() -> fs.getAttributes(file));
  }

  /**
   * See documentation for {@link RefreshWorker#computeAttributesForFile(NewVirtualFileSystem, VirtualFile)}
   */
  private static String @NotNull [] computeListWithCaching(LocalFileSystemImpl fs, VirtualFile dir, Set<String> filter) {
    return Cancellation.computeInNonCancelableSection(() -> fs.listWithCaching(dir, filter));
  }

  private ChildInfo childRecord(NewVirtualFileSystem fs, FakeVirtualFile child, FileAttributes attributes, boolean canonicalize) {
    var t = System.nanoTime();
    String name = canonicalize ? fs.getCanonicallyCasedName(child) : child.getName();
    boolean isEmptyDir = attributes.isDirectory() && !fs.hasChildren(child);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(child) : null;
    myIoTime.addAndGet(System.nanoTime() - t);
    int nameId = myPersistencePeer.getNameId(name);
    return new ChildInfoImpl(nameId, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget);
  }

  private void generateDeleteEvents(List<VFileEvent> events,
                                    VirtualDirectoryImpl dir,
                                    Set<String> deletedNames,
                                    ObjectOpenCustomHashSet<String> actualNames,
                                    List<ChildInfo> newKids) {
    for (String name : deletedNames) {
      VirtualFileSystemEntry child = dir.findChild(name);
      if (child != null) {
        if (checkAndScheduleFileNameChange(events, actualNames, child)) {
          newKids.removeIf(newKidCandidate -> StringUtilRt.equal(newKidCandidate.getName(), child.getName(), true));
        }
        else {
          scheduleDeletion(events, child);
        }
      }
    }
  }

  private void generateCreateEvents(List<VFileEvent> events, VirtualDirectoryImpl dir, List<ChildInfo> newKids) {
    for (ChildInfo record : newKids) {
      scheduleCreation(events, dir, record.getName().toString(), record.getFileAttributes(), record.getSymlinkTarget());
    }
  }

  private void generateUpdateEvents(List<VFileEvent> events,
                                    NewVirtualFileSystem fs,
                                    VirtualDirectoryImpl dir,
                                    ObjectOpenCustomHashSet<String> actualNames,
                                    List<Pair<VirtualFile, @Nullable FileAttributes>> existingMap) {
    for (Pair<VirtualFile, FileAttributes> pair : existingMap) {
      NewVirtualFile child = (NewVirtualFile)pair.first;
      FileAttributes childAttributes = pair.second;
      if (childAttributes != null) {
        checkAndScheduleChildRefresh(events, fs, dir, child, childAttributes, true);
        checkAndScheduleFileNameChange(events, actualNames, child);
      }
      else {
        scheduleDeletion(events, child);
      }
    }
  }

  private static void clearFsCache(NewVirtualFileSystem fs) {
    if (fs instanceof LocalFileSystemImpl) {
      ((LocalFileSystemImpl)fs).clearListCache();
    }
  }

  private static final class RefreshCancelledException extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  private void checkCancelled(NewVirtualFile stopAt) throws RefreshCancelledException {
    Consumer<? super VirtualFile> testListener = ourTestListener;
    if (testListener != null) {
      testListener.accept(stopAt);
    }
    if (myCancelled) {
      if (LOG.isTraceEnabled()) LOG.trace("cancelled at: " + stopAt);
      forceMarkDirty(stopAt);
      synchronized (this) {
        NewVirtualFile file;
        while ((file = myRefreshQueue.poll()) != null) {
          forceMarkDirty(file);
          mySemaphore.up();
        }
      }
      throw new RefreshCancelledException();
    }
  }

  private static void forceMarkDirty(NewVirtualFile file) {
    file.markClean();  // otherwise, consequent markDirty() won't have any effect
    file.markDirty();
  }

  private void scheduleDeletion(List<VFileEvent> events, VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    events.add(new VFileDeleteEvent(myRequestor, file));
  }

  private void scheduleCreation(List<VFileEvent> events, NewVirtualFile parent, String childName, FileAttributes attributes, @Nullable String symlinkTarget) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("create parent=" + parent + " name=" + childName + " attr=" + attributes);
    }

    ChildInfo[] children = null;
    if (attributes.isDirectory() && !attributes.isSymLink() && parent.getFileSystem() instanceof LocalFileSystem) {
      try {
        Path childPath = getChildPath(parent.getPath(), childName);
        if (childPath != null && shouldScanDirectory(parent, childPath, childName)) {
          List<Path> relevantExcluded = ContainerUtil.mapNotNull(ProjectManagerEx.getInstanceEx().getAllExcludedUrls(), url -> {
            Path path = Path.of(VirtualFileManager.extractPath(url));
            return path.startsWith(childPath) ? path : null;
          });
          var t = System.nanoTime();
          children = scanChildren(childPath, relevantExcluded, parent);
          myIoTime.addAndGet(System.nanoTime() - t);
        }
      }
      catch (InvalidPathException e) {
        LOG.warn("Invalid child name: '" + childName + "'", e);
      }
    }

    events.add(new VFileCreateEvent(myRequestor, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, children));

    VFileEvent event = ((PersistentFSImpl)myPersistence).generateCaseSensitivityChangedEventForUnknownCase(parent, childName);
    if (event != null) {
      events.add(event);
    }
  }

  private static @Nullable Path getChildPath(String parentPath, String childName) {
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

  // scan all children of "root" (except excluded dirs) recursively and return them in the ChildInfo[] array
  // `null` means error during scan
  private ChildInfo @Nullable [] scanChildren(Path root, List<Path> excluded, NewVirtualFile currentDir) {
    // the stack contains a list of children found so far in the current directory
    Stack<List<ChildInfo>> stack = new Stack<>();
    int nameId = myPersistencePeer.getNameId("");
    ChildInfo fakeRoot = new ChildInfoImpl(nameId, null, null, null);
    stack.push(new SmartList<>(fakeRoot));
    FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      private int checkCanceledCount;

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(root)) {
          visitFile(dir, attrs);
        }
        if (SystemInfoRt.isWindows && attrs.isOther()) {
          return FileVisitResult.SKIP_SUBTREE;  // bypassing NTFS reparse points
        }
        // on average, this "excluded" array is small for any particular root, so linear search it is.
        if (excluded.contains(dir)) {
          // skipping excluded roots (record its attributes nevertheless), even if we have content roots beneath
          // stop optimization right here - it's too much pain to track all these nested content/excluded/content otherwise
          return FileVisitResult.SKIP_SUBTREE;
        }
        stack.push(new ArrayList<>());
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if ((++checkCanceledCount & 0xf) == 0) {
          checkCancelled(currentDir);
        }
        FileAttributes attributes = FileAttributes.fromNio(file, attrs);
        String symLinkTarget = attrs.isSymbolicLink() ? FileUtilRt.toSystemIndependentName(file.toRealPath().toString()) : null;
        int nameId = myPersistencePeer.getNameId(file.getFileName().toString());
        ChildInfo info = new ChildInfoImpl(nameId, attributes, null, symLinkTarget);
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
        return FileVisitResult.CONTINUE;  // ignoring exceptions from short-living temp files
      }
    };

    try {
      Files.walkFileTree(root, visitor);
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;  // tell the client we didn't find any children, abandon the optimization altogether
    }

    return stack.pop().get(0).getChildren();
  }

  private void checkAndScheduleChildRefresh(List<VFileEvent> events,
                                            NewVirtualFileSystem fs,
                                            @Nullable NewVirtualFile parent,
                                            NewVirtualFile child,
                                            FileAttributes childAttributes,
                                            boolean enqueue) {
    boolean fileDirty = child.isDirty();
    if (LOG.isTraceEnabled()) LOG.trace("file=" + child + " dirty=" + fileDirty);
    if (!fileDirty) {
      return;
    }

    if (checkAndScheduleFileTypeChange(events, fs, parent, child, childAttributes)) {
      child.markClean();
      return;
    }

    checkWritableAttributeChange(events, child, myPersistence.isWritable(child), childAttributes.isWritable());

    if (SystemInfoRt.isWindows) {
      checkHiddenAttributeChange(events, child, child.is(VFileProperty.HIDDEN), childAttributes.isHidden());
    }

    if (childAttributes.isSymLink()) {
      var t = System.nanoTime();
      var target = fs.resolveSymLink(child);
      myIoTime.addAndGet(System.nanoTime() - t);
      checkSymbolicLinkChange(events, child, child.getCanonicalPath(), target);
    }

    if (!childAttributes.isDirectory()) {
      long oldTimestamp = myPersistence.getTimeStamp(child), newTimestamp = childAttributes.lastModified;
      long oldLength = myPersistence.getLastRecordedLength(child), newLength = childAttributes.length;
      if (oldTimestamp != newTimestamp || oldLength != newLength) {
        if (LOG.isTraceEnabled()) LOG.trace(
          "update file=" + child +
          (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
          (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
        events.add(new VFileContentChangeEvent(myRequestor, child, child.getModificationStamp(), VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH, oldTimestamp, newTimestamp, oldLength, newLength));
      }
      child.markClean();
    }
    else if (enqueue && myIsRecursive) {
      if (child instanceof VirtualDirectoryImpl) {
        mySemaphore.down();
        myRefreshQueue.add(child);
      }
      else {
        LOG.error("not a directory: " + child + " (" + child.getClass() + ')');
      }
    }
  }

  private boolean checkAndScheduleFileTypeChange(List<VFileEvent> events,
                                                 NewVirtualFileSystem fs,
                                                 @Nullable NewVirtualFile parent,
                                                 NewVirtualFile child,
                                                 FileAttributes childAttributes) {
    boolean currentIsDirectory = child.isDirectory(), upToDateIsDirectory = childAttributes.isDirectory();
    boolean currentIsSymlink = child.is(VFileProperty.SYMLINK), upToDateIsSymlink = childAttributes.isSymLink();
    boolean currentIsSpecial = child.is(VFileProperty.SPECIAL), upToDateIsSpecial = childAttributes.isSpecial();

    boolean isFileTypeChanged = currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial;
    if (currentIsDirectory != upToDateIsDirectory ||
        (isFileTypeChanged && !Boolean.getBoolean("refresh.ignore.file.type.changes"))) {
      scheduleDeletion(events, child);
      if (parent != null) {
        var t = System.nanoTime();
        String symlinkTarget = upToDateIsSymlink ? fs.resolveSymLink(child) : null;
        myIoTime.addAndGet(System.nanoTime() - t);
        scheduleCreation(events, parent, child.getName(), childAttributes, symlinkTarget);
      }
      else {
        LOG.error("transgender orphan: " + child + ' ' + childAttributes);
      }
      return true;
    }

    return false;
  }

  private boolean checkAndScheduleFileNameChange(List<VFileEvent> events,
                                                 @Nullable ObjectOpenCustomHashSet<String> actualNames,
                                                 VirtualFile child) {
    if (actualNames != null) {
      String currentName = child.getName();
      String actualName = actualNames.get(currentName);
      if (actualName != null && !currentName.equals(actualName)) {
        scheduleAttributeChange(events, child, VirtualFile.PROP_NAME, currentName, actualName);
        return true;
      }
    }
    return false;
  }

  private void checkWritableAttributeChange(List<VFileEvent> events, VirtualFile file, boolean oldWritable, boolean newWritable) {
    if (oldWritable != newWritable) {
      scheduleAttributeChange(events, file, VirtualFile.PROP_WRITABLE, oldWritable, newWritable);
    }
  }

  private void checkHiddenAttributeChange(List<VFileEvent> events, VirtualFile child, boolean oldHidden, boolean newHidden) {
    if (oldHidden != newHidden) {
      scheduleAttributeChange(events, child, VirtualFile.PROP_HIDDEN, oldHidden, newHidden);
    }
  }

  private void checkSymbolicLinkChange(List<VFileEvent> events, VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtilRt.toSystemIndependentName(currentTarget) : null;
    if (!Objects.equals(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(events, child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  private void scheduleAttributeChange(List<VFileEvent> events,
                                       VirtualFile file,
                                       @VirtualFile.PropName String property,
                                       Object current,
                                       Object upToDate) {
    if (LOG.isTraceEnabled()) LOG.trace("update file=" + file + ' ' + property + '=' + current + "->" + upToDate);
    events.add(new VFilePropertyChangeEvent(myRequestor, file, property, current, upToDate));
  }

  static Consumer<? super VirtualFile> ourTestListener;
}
