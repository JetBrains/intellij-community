// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.MathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class RefreshWorker {
  private static final Logger LOG = Logger.getInstance(RefreshWorker.class);

  private static final int ourParallelism =
    MathUtil.clamp(Registry.intValue("vfs.refresh.worker.parallelism", 4), 1, Runtime.getRuntime().availableProcessors());
  private static final ExecutorService ourExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("VFS Refresh", ourParallelism);

  private final boolean myIsRecursive;
  private final boolean myParallel;
  private final Deque<NewVirtualFile> myRefreshQueue = new ConcurrentLinkedDeque<>();
  private final Semaphore mySemaphore = new Semaphore();
  private volatile boolean myCancelled;

  private final AtomicInteger myFullScans = new AtomicInteger(), myPartialScans = new AtomicInteger(), myProcessed = new AtomicInteger();
  private final AtomicLong myVfsTime = new AtomicLong(), myIoTime = new AtomicLong();

  RefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myParallel = isRecursive && ourParallelism > 1 && !ApplicationManager.getApplication().isWriteThread();
    myRefreshQueue.addLast(refreshRoot);
  }

  void cancel() {
    myCancelled = true;
  }

  @NotNull List<VFileEvent> scan() {
    var t = System.nanoTime();

    try {
      NewVirtualFile root = myRefreshQueue.removeFirst();
      NewVirtualFileSystem fs = root.getFileSystem();
      PersistentFS persistence = PersistentFS.getInstance();
      var events = new ArrayList<VFileEvent>();

      FileAttributes attributes = fs.getAttributes(root);
      if (attributes == null) {
        scheduleDeletion(events, root);
        root.markClean();
        return events;
      }

      checkAndScheduleChildRefresh(events, fs, persistence, root.getParent(), root, attributes);

      if (root.isDirty() && root.isDirectory() && myRefreshQueue.isEmpty()) {
        queueDirectory(root);
      }

      if (!myParallel) {
        try {
          processQueue(events, PersistentFS.replaceWithNativeFS(fs), persistence);
        }
        catch (RefreshCancelledException e) {
          LOG.trace("refresh cancelled");
        }
      }
      else {
        List<CompletableFuture<List<VFileEvent>>> futures = new ArrayList<>(ourParallelism);
        for (int i = 0; i < ourParallelism; i++) {
          futures.add(CompletableFuture.supplyAsync(() -> {
            var threadEvents = new ArrayList<VFileEvent>();
            try {
              processQueue(threadEvents, PersistentFS.replaceWithNativeFS(fs), persistence);
            }
            catch (RefreshCancelledException ignored) { }
            catch (Throwable e) {
              LOG.error(e);
              myCancelled = true;
            }
            return threadEvents;
          }, ourExecutor));
        }
        for (CompletableFuture<List<VFileEvent>> f : futures) {
          try {
            events.addAll(f.get());
          }
          catch (InterruptedException ignored) { }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
        if (myCancelled) {
          LOG.trace("refresh cancelled");
        }
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

  private void queueDirectory(NewVirtualFile root) {
    if (root instanceof VirtualDirectoryImpl) {
      mySemaphore.down();
      myRefreshQueue.addLast(root);
    }
    else {
      LOG.error("not a directory: " + root + " (" + root.getClass() + ')');
    }
  }

  private void processQueue(List<VFileEvent> events, NewVirtualFileSystem fs, PersistentFS persistence) throws RefreshCancelledException {
    nextDir:
    while (!mySemaphore.isUp()) {
      var dir = (VirtualDirectoryImpl)myRefreshQueue.pollFirst();
      if (dir == null) {
        TimeoutUtil.sleep(10);
        continue;
      }

      try {
        boolean fullSync = dir.allChildrenLoaded(), succeeded;

        do {
          (fullSync ? myFullScans : myPartialScans).incrementAndGet();
          var mark = events.size();
          try {
            succeeded = fullSync ? fullDirRefresh(events, fs, persistence, dir) : partialDirRefresh(events, fs, persistence, dir);
          }
          catch (InvalidVirtualFileAccessException e) {
            events.subList(mark, events.size()).clear();
            continue nextDir;
          }
          if (!succeeded) {
            events.subList(mark, events.size()).clear();
            if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          }
        }
        while (!succeeded);
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

  private boolean fullDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, PersistentFS persistence, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<List<String>, List<VirtualFile>> snapshot = ReadAction.compute(() -> {
      VirtualFile[] children = dir.getChildren();
      return new Pair<>(getNames(children), Arrays.asList(children));
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    if (snapshot == null) {
      return false;
    }
    List<String> persistedNames = snapshot.getFirst();
    List<VirtualFile> children = snapshot.getSecond();

    t = System.nanoTime();
    Map<String, FileAttributes> childrenWithAttributes = fs instanceof BatchingFileSystem ? ((BatchingFileSystem)fs).listWithAttributes(dir) : null;
    String[] listDir = childrenWithAttributes != null ? ArrayUtil.toStringArray(childrenWithAttributes.keySet()) : fs.list(dir);
    myIoTime.addAndGet(System.nanoTime() - t);
    String[] upToDateNames = VfsUtil.filterNames(listDir);
    Set<String> newNames = new HashSet<>(upToDateNames.length);
    ContainerUtil.addAll(newNames, upToDateNames);
    if (dir.allChildrenLoaded() && children.size() < upToDateNames.length) {
      for (VirtualFile child : children) {
        newNames.remove(child.getName());
      }
    }
    else {
      //noinspection SlowAbstractSetRemoveAll
      newNames.removeAll(persistedNames);
    }

    Set<String> deletedNames = new HashSet<>(persistedNames);
    ContainerUtil.removeAll(deletedNames, upToDateNames);

    ObjectOpenCustomHashSet<String> actualNames =
      dir.isCaseSensitive() ? null : (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(upToDateNames, false);
    if (LOG.isTraceEnabled()) {
      LOG.trace("current=" + persistedNames + " +" + newNames + " -" + deletedNames);
    }

    List<ChildInfo> newKids = new ArrayList<>(newNames.size());
    for (String newName : newNames) {
      checkCancelled(dir);
      ChildInfo record = childRecord(fs, dir, newName, false);
      if (record != null) {
        newKids.add(record);
      }
      else if (LOG.isTraceEnabled()) {
        LOG.trace("[+] fs=" + fs + " dir=" + dir + " name=" + newName);
      }
    }

    List<Pair<VirtualFile, FileAttributes>> updatedMap = new ArrayList<>(children.size() - deletedNames.size());
    List<VirtualFile> chs = ContainerUtil.filter(children, file -> !deletedNames.contains(file.getName()));

    if (fs instanceof BatchingFileSystem) {
      Set<String> names = ContainerUtil.map2Set(chs, file -> file.getName());
      Map<String, FileAttributes> map = ContainerUtil.filter(childrenWithAttributes, s -> names.contains(s));
      Map<String, VirtualFile> nameToFile = new HashMap<>();
      for (VirtualFile file : chs) {
        nameToFile.put(file.getName(), file);
      }
      for (Map.Entry<String, FileAttributes> e : map.entrySet()) {
        String name = e.getKey();
        FileAttributes attributes = e.getValue();
        updatedMap.add(new Pair<>(nameToFile.get(name), attributes));
      }
    }
    else {
      t = System.nanoTime();
      for (VirtualFile child : chs) {
        checkCancelled(dir);
        updatedMap.add(new Pair<>(child, fs.getAttributes(child)));
      }
      myIoTime.addAndGet(System.nanoTime() - t);
    }

    if (isFullScanDirectoryChanged(dir, persistedNames, children)) {
      return false;
    }

    for (String name : deletedNames) {
      VirtualFileSystemEntry child = dir.findChild(name);
      if (child != null) {
        if (checkAndScheduleFileNameChange(events, actualNames, child)) {
          newKids.removeIf(newKidCandidate -> StringUtil.equalsIgnoreCase(newKidCandidate.getName(), child.getName()));
        }
        else {
          scheduleDeletion(events, child);
        }
      }
    }

    for (ChildInfo record : newKids) {
      scheduleCreation(events, dir, record.getName().toString(), record.getFileAttributes(), record.getSymlinkTarget());
    }

    for (Pair<VirtualFile, FileAttributes> pair : updatedMap) {
      NewVirtualFile child = (NewVirtualFile)pair.first;
      checkCancelled(child);
      FileAttributes childAttributes = pair.second;
      if (childAttributes != null) {
        checkAndScheduleChildRefresh(events, fs, persistence, dir, child, childAttributes);
        checkAndScheduleFileNameChange(events, actualNames, child);
      }
      else {
        if (LOG.isTraceEnabled()) LOG.trace("[x] fs=" + fs + " dir=" + dir + " name=" + child.getName());
        scheduleDeletion(events, child);
      }
    }

    return !isFullScanDirectoryChanged(dir, persistedNames, children);
  }

  private boolean isFullScanDirectoryChanged(VirtualDirectoryImpl dir, List<String> names, List<VirtualFile> children) {
    var t = System.nanoTime();
    var changed = ReadAction.compute(() -> {
      checkCancelled(dir);
      VirtualFile[] currentChildren = dir.getChildren();
      return !children.equals(Arrays.asList(currentChildren)) || !names.equals(getNames(currentChildren));
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  private static List<String> getNames(VirtualFile[] children) {
    return ContainerUtil.map(children, VirtualFile::getName);
  }

  private boolean partialDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, PersistentFS persistence, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<List<VirtualFile>, List<String>> snapshot = ReadAction.compute(() -> {
      checkCancelled(dir);
      return new Pair<>(dir.getCachedChildren(), dir.getSuspiciousNames());
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    List<VirtualFile> cached = snapshot.getFirst();
    List<String> wanted = snapshot.getSecond();

    ObjectOpenCustomHashSet<String> actualNames;
    if (dir.isCaseSensitive() || cached.isEmpty()) {
      actualNames = null;
    }
    else {
      t = System.nanoTime();
      actualNames = (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(VfsUtil.filterNames(fs.list(dir)), false);
      myIoTime.addAndGet(System.nanoTime() - t);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("cached=" + cached + " actual=" + actualNames + " suspicious=" + wanted);
    }

    List<Pair<VirtualFile, FileAttributes>> existingMap = new ArrayList<>(cached.size());
    t = System.nanoTime();
    for (VirtualFile child : cached) {
      checkCancelled(dir);
      existingMap.add(new Pair<>(child, fs.getAttributes(child)));
    }
    myIoTime.addAndGet(System.nanoTime() - t);

    List<ChildInfo> newKids = new ArrayList<>(wanted.size());
    for (String name : wanted) {
      if (name.isEmpty()) continue;
      checkCancelled(dir);
      ChildInfo record = childRecord(fs, dir, name, true);
      if (record != null) {
        newKids.add(record);
      }
    }

    if (isDirectoryChanged(dir, cached, wanted)) {
      return false;
    }

    for (Pair<VirtualFile, FileAttributes> pair : existingMap) {
      NewVirtualFile child = (NewVirtualFile)pair.first;
      checkCancelled(child);
      FileAttributes childAttributes = pair.second;
      if (childAttributes != null) {
        checkAndScheduleChildRefresh(events, fs, persistence, dir, child, childAttributes);
        checkAndScheduleFileNameChange(events, actualNames, child);
      }
      else {
        scheduleDeletion(events, child);
      }
    }

    for (ChildInfo record : newKids) {
      scheduleCreation(events, dir, record.getName().toString(), record.getFileAttributes(), record.getSymlinkTarget());
    }

    return !isDirectoryChanged(dir, cached, wanted);
  }

  private boolean isDirectoryChanged(VirtualDirectoryImpl dir, List<VirtualFile> cached, List<String> wanted) {
    var t = System.nanoTime();
    var changed = ReadAction.compute(() -> {
      checkCancelled(dir);
      return !cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames());
    });
    myVfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  private @Nullable ChildInfo childRecord(NewVirtualFileSystem fs, VirtualFile dir, String name, boolean canonicalize) {
    FakeVirtualFile file = new FakeVirtualFile(dir, name);
    var t = System.nanoTime();
    FileAttributes attributes = fs.getAttributes(file);
    if (attributes == null) {
      myIoTime.addAndGet(System.nanoTime() - t);
      return null;
    }
    boolean isEmptyDir = attributes.isDirectory() && !fs.hasChildren(file);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(file) : null;
    if (canonicalize) {
      name = fs.getCanonicallyCasedName(file);  // we need case-exact names in file events
    }
    myIoTime.addAndGet(System.nanoTime() - t);
    return new ChildInfoImpl(name, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget);
  }

  private static class RefreshCancelledException extends RuntimeException {
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
        while ((file = myRefreshQueue.pollFirst()) != null) {
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

  private static void scheduleDeletion(List<VFileEvent> events, VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    events.add(new VFileDeleteEvent(null, file, true));
  }

  private void scheduleCreation(List<VFileEvent> events, NewVirtualFile parent, String childName, FileAttributes attributes, @Nullable String symlinkTarget) {
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
          children = scanChildren(childPath, relevantExcluded, () -> checkCancelled(parent));
        }
      }
      catch (InvalidPathException e) {
        LOG.warn("Invalid child name: '" + childName + "'", e);
      }
    }
    events.add(new VFileCreateEvent(null, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, true, children));
    VFileEvent event = VirtualDirectoryImpl.generateCaseSensitivityChangedEventForUnknownCase(parent, childName);
    if (event != null) {
      events.add(event);
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

  // scan all children of "root" (except excluded dirs) recursively and return them in the ChildInfo[] array
  // `null` means error during scan
  private static ChildInfo @Nullable [] scanChildren(Path root, List<Path> excluded, Runnable checkCanceled) {
    // the stack contains a list of children found so far in the current directory
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
          // skipping excluded roots (just record its attributes nevertheless), even if we have content roots beneath
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
                                            PersistentFS persistence,
                                            @Nullable NewVirtualFile parent,
                                            NewVirtualFile child,
                                            FileAttributes childAttributes) {
    boolean fileDirty = child.isDirty();
    if (LOG.isTraceEnabled()) LOG.trace("file=" + child + " dirty=" + fileDirty);
    if (!fileDirty) {
      return;
    }

    if (checkAndScheduleFileTypeChange(events, fs, parent, child, childAttributes)) {
      child.markClean();
      return;
    }

    checkWritableAttributeChange(events, child, persistence.isWritable(child), childAttributes.isWritable());

    if (SystemInfo.isWindows) {
      checkHiddenAttributeChange(events, child, child.is(VFileProperty.HIDDEN), childAttributes.isHidden());
    }

    if (childAttributes.isSymLink()) {
      var t = System.nanoTime();
      var target = fs.resolveSymLink(child);
      myIoTime.addAndGet(System.nanoTime() - t);
      checkSymbolicLinkChange(events, child, child.getCanonicalPath(), target);
    }

    if (!childAttributes.isDirectory()) {
      long oldTimestamp = persistence.getTimeStamp(child), newTimestamp = childAttributes.lastModified;
      long oldLength = persistence.getLastRecordedLength(child), newLength = childAttributes.length;
      if (oldTimestamp != newTimestamp || oldLength != newLength) {
        if (LOG.isTraceEnabled()) LOG.trace(
          "update file=" + child +
          (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
          (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
        events.add(new VFileContentChangeEvent(null, child, child.getModificationStamp(), -1, oldTimestamp, newTimestamp, oldLength, newLength, true));
      }
      child.markClean();
    }
    else if (myIsRecursive) {
      queueDirectory(child);
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

  // true if the event was scheduled
  private static boolean checkAndScheduleFileNameChange(List<VFileEvent> events,
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

  private static void checkWritableAttributeChange(List<VFileEvent> events, VirtualFile file, boolean oldWritable, boolean newWritable) {
    if (oldWritable != newWritable) {
      scheduleAttributeChange(events, file, VirtualFile.PROP_WRITABLE, oldWritable, newWritable);
    }
  }

  private static void checkHiddenAttributeChange(List<VFileEvent> events, VirtualFile child, boolean oldHidden, boolean newHidden) {
    if (oldHidden != newHidden) {
      scheduleAttributeChange(events, child, VirtualFile.PROP_HIDDEN, oldHidden, newHidden);
    }
  }

  private static void checkSymbolicLinkChange(List<VFileEvent> events, VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtil.toSystemIndependentName(currentTarget) : null;
    if (!Objects.equals(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(events, child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  private static void scheduleAttributeChange(List<VFileEvent> events,
                                              VirtualFile file,
                                              @VirtualFile.PropName String property,
                                              Object current,
                                              Object upToDate) {
    if (LOG.isTraceEnabled()) LOG.trace("update file=" + file + ' ' + property + '=' + current + "->" + upToDate);
    events.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }

  static Consumer<? super VirtualFile> ourTestListener;
}
