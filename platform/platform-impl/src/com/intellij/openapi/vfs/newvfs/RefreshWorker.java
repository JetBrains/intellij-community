// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.MathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.intellij.util.containers.CollectionFactory.createFilePathMap;
import static com.intellij.util.containers.CollectionFactory.createFilePathSet;
import static com.intellij.util.containers.FastUtilHashingStrategies.getCaseInsensitiveStringStrategy;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class RefreshWorker {
  private static final Logger LOG = Logger.getInstance(RefreshWorker.class);

  private static final int PARALLELISM = MathUtil.clamp(
    Registry.intValue("vfs.refresh.worker.parallelism", 6),
    1, Runtime.getRuntime().availableProcessors()
  );

  private static final Executor rawExecutor = ExecutorsKt.asExecutor(
      Dispatchers.getIO().limitedParallelism(PARALLELISM, "RefreshWorkerDispatcher")
  );

  /** Wraps {@link #rawExecutor} to propagate IntelliJ thread context to worker threads. */
  private static final Executor executor = command -> rawExecutor.execute(
    AppScheduledExecutorService.capturePropagationAndCancellationContext(command)
  );

  private static final Object REQUESTOR = VFileEvent.REFRESH_REQUESTOR;

  private final boolean isRecursive;
  private final boolean parallel;

  private final Set<NewVirtualFile> roots;
  private final Queue<NewVirtualFile> refreshQueue;
  private final Semaphore semaphore;

  private final PersistentFS persistentFS = PersistentFS.getInstance();
  private final FSRecordsImpl vfsPeer = ((PersistentFSImpl)persistentFS).peer();
  private final ChildScanner childScanner;

  private volatile boolean cancelled;

  // =========================== monitoring =========================================================================== //
  private final AtomicInteger fullScans = new AtomicInteger();
  private final AtomicInteger partialScans = new AtomicInteger();
  private final AtomicInteger queryItemsProcessed = new AtomicInteger();
  /** Total time (ns) since instance creation, spent on VFS (i.e. potentially cached) requests */
  private final AtomicLong vfsTime = new AtomicLong();
  /** Total time (ns) since instance creation, spent on IO -- usually, via {@link VirtualFileSystem} */
  private final AtomicLong ioTime = new AtomicLong();

  RefreshWorker(Collection<NewVirtualFile> refreshRoots, boolean isRecursive) {
    this.isRecursive = isRecursive;
    parallel = isRecursive
               && (PARALLELISM > 1 && !ApplicationManager.getApplication().isWriteIntentLockAcquired());
    roots = new HashSet<>(refreshRoots);
    refreshQueue = new LinkedBlockingQueue<>(refreshRoots);
    semaphore = new Semaphore(refreshRoots.size());
    childScanner = Registry.is("vfs.refresh.use.transient.files.for.children.preloading", true)
                   ? new TransientChildScanner(file -> checkCancelled((NewVirtualFile)file))
                   : new NioChildScanner(file1 -> checkCancelled((NewVirtualFile)file1));
  }

  void cancel() {
    cancelled = true;
  }

  List<VFileEvent> scan() {
    var t = System.nanoTime();
    try {
      var events = new ArrayList<VFileEvent>();
      if (!parallel) {
        singleThreadScan(events);
      }
      else {
        parallelScan(events);
      }
      return events;
    }
    finally {
      t = NANOSECONDS.toMillis(System.nanoTime() - t);
      var retries = fullScans.get() + partialScans.get() - queryItemsProcessed.get();
      VfsUsageCollector.logRefreshScan(fullScans.get(), partialScans.get(), retries, t,
                                       NANOSECONDS.toMillis(vfsTime.get()), NANOSECONDS.toMillis(ioTime.get()));
    }
  }

  List<VFileEvent> scanNewFiles(@NotNull Map<NewVirtualFile, ? extends Collection<String>> newFilesCaseSensitive) {
    var events = new ArrayList<VFileEvent>(newFilesCaseSensitive.size());
    for (var entry : newFilesCaseSensitive.entrySet()) {
      var parent = entry.getKey();
      var fs = parent.getFileSystem();
      Collection<String> childNames = createFilePathSet(entry.getValue(), parent.isCaseSensitive());
      for (var childName : childNames) {
        if (cancelled) return events;
        if (VfsUtil.isBadName(childName)) continue;

        var fakeChild = new FakeVirtualFile(parent, childName);
        var attributes = computeAttributesForFile(fs, fakeChild);
        if (attributes == null) continue;

        var canonicalName = fs.getCanonicallyCasedName(fakeChild);
        var symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(fakeChild) : null;
        scheduleCreation(events, parent, canonicalName, attributes, symlinkTarget);
      }
    }
    return events;
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
    var futures = new ArrayList<CompletableFuture<List<VFileEvent>>>(PARALLELISM);

    for (var i = 0; i < PARALLELISM; i++) {
      futures.add(CompletableFuture.supplyAsync(() -> {
        var threadEvents = new ArrayList<VFileEvent>();
        try {
          processQueue(threadEvents);
        }
        catch (RefreshCancelledException ignored) { }
        catch (CancellationException e) {
          cancelled = true;
        }
        catch (Throwable t) {
          LOG.error(t);
          cancelled = true;
        }
        return threadEvents;
      }, executor));
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

    if (cancelled) {
      LOG.trace("refresh cancelled [MT]");
    }
  }

  private void processQueue(/*OutParam*/ List<VFileEvent> events) throws RefreshCancelledException {
    nextDir:
    while (!semaphore.isUp()) {
      var file = refreshQueue.poll();
      if (file == null) {
        TimeoutUtil.sleep(1);
        continue;
      }

      var fs = file.getFileSystem();

      if (fs instanceof AsyncableFileSystem afs) {
        //can't refresh file that has async ops still in flight => flush them:
        try {
          afs.fsync(file);
        }
        catch (IOException e) {
          continue;
        }
      }

      try {
        if (roots.contains(file)) {
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
          boolean fullSync = dir.allChildrenLoaded();
          try {
            boolean success;
            if (fullSync) {
              fullScans.incrementAndGet();
              success = fullDirRefresh(events, fs, dir);
            }
            else {
              partialScans.incrementAndGet();
              success = partialDirRefresh(events, fs, dir);
            }
            if (success) break;

            events.subList(mark, events.size()).clear();
            if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          }
          catch (InvalidVirtualFileAccessException e) {
            events.subList(mark, events.size()).clear();
            continue nextDir;
          }
        }
        queryItemsProcessed.incrementAndGet();

        if (isRecursive) {
          dir.markClean();
        }
      }
      finally {
        semaphore.up();
      }
    }
  }

  private boolean fullDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<VirtualFile[], List<String>> snapshot = ReadAction.computeBlocking(() -> {
      VirtualFile[] children = dir.getChildren();
      return new Pair<>(children, getNames(children));
    });
    vfsTime.addAndGet(System.nanoTime() - t);
    VirtualFile[] vfsChildren = snapshot.first;
    List<String> vfsNames = snapshot.second;

    boolean dirIsCaseSensitive = dir.isCaseSensitive();

    Map<String, FileAttributes> childrenWithAttributes;
    t = System.nanoTime();
    if (fs instanceof BatchingFileSystem) {
      childrenWithAttributes = adjustCaseSensitivity(
        computeAllChildrenAttributes((BatchingFileSystem)fs, dir, /*filter: */null),
        dirIsCaseSensitive
      );
    }
    else {
      String[] childrenNames = fs.list(dir);
      childrenWithAttributes = createFilePathMap(childrenNames.length, dirIsCaseSensitive);
      for (String name : childrenNames) {
        childrenWithAttributes.put(name, null);
      }
      if (childrenWithAttributes.size() != childrenNames.length) {
        //TODO RC: seems like dir.isCaseSensitive() is wrong/outdated (i.e. actual dir case-sensitivity is different from
        //         FS-default, and it wasn't yet determined).
        //         We should re-query dir.case-sensitivity
      }
    }
    ioTime.addAndGet(System.nanoTime() - t);

    Set<String> newNames = createFilePathSet(childrenWithAttributes.keySet(), dirIsCaseSensitive);
    vfsNames.forEach(newNames::remove);

    Set<String> deletedNames = createFilePathSet(vfsNames, dirIsCaseSensitive);
    childrenWithAttributes.keySet().forEach(deletedNames::remove);

    ObjectOpenCustomHashSet<String> actualNames = dirIsCaseSensitive ?
                                                  null :
                                                  new ObjectOpenCustomHashSet<>(
                                                    childrenWithAttributes.keySet(),
                                                    getCaseInsensitiveStringStrategy()
                                                  );
    if (LOG.isTraceEnabled()) {
      LOG.trace("current=" + vfsNames + " +" + newNames + " -" + deletedNames);
    }

    List<ChildInfo> newKids = newNames.isEmpty() && deletedNames.isEmpty() ?
                              List.of() :
                              new ArrayList<>(newNames.size());
    for (String newName : newNames) {
      if (VfsUtil.isBadName(newName)) continue;
      FakeVirtualFile child = new FakeVirtualFile(dir, newName);
      FileAttributes attributes = getAttributes(fs, childrenWithAttributes, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, false));
      }
    }

    List<Pair<VirtualFile, FileAttributes>> existingMap = new ArrayList<>(vfsChildren.length - deletedNames.size());
    for (VirtualFile child : vfsChildren) {
      if (!deletedNames.contains(child.getName())) {
        existingMap.add(new Pair<>(child, getAttributes(fs, childrenWithAttributes, child)));
      }
    }

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
    var changed = ReadAction.computeBlocking(() -> {
      VirtualFile[] currentChildren = dir.getChildren();
      return !Arrays.equals(children, currentChildren) || !names.equals(getNames(currentChildren));
    });
    vfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  private boolean partialDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) {
    var t = System.nanoTime();
    Pair<List<VirtualFile>, List<String>> snapshot = ReadAction.computeBlocking(
      () -> new Pair<>(dir.getCachedChildren(), dir.getSuspiciousNames())
    );
    vfsTime.addAndGet(System.nanoTime() - t);
    List<VirtualFile> cached = snapshot.first;
    List<String> wanted = snapshot.second;

    boolean dirIsCaseSensitive = dir.isCaseSensitive();
    Set<String> namesToRefresh = createFilePathSet(wanted, dirIsCaseSensitive);
    for (VirtualFile file : cached) namesToRefresh.add(file.getName());

    Map<String, FileAttributes> childrenWithAttributes = null;
    if (fs instanceof BatchingFileSystem batchingFileSystem) {
      t = System.nanoTime();
      childrenWithAttributes = adjustCaseSensitivity(
        computeAllChildrenAttributes(batchingFileSystem, dir, namesToRefresh),
        dirIsCaseSensitive
      );
      ioTime.addAndGet(System.nanoTime() - t);
    }

    ObjectOpenCustomHashSet<String> actualNames;
    if (dirIsCaseSensitive || cached.isEmpty()) {
      actualNames = null;
    }
    else if (childrenWithAttributes != null) {
      actualNames = (ObjectOpenCustomHashSet<String>)createFilePathSet(childrenWithAttributes.keySet(), /*caseSensitive: */ false);
    }
    else {
      t = System.nanoTime();
      String[] childrenNames = fs.list(dir);
      actualNames = (ObjectOpenCustomHashSet<String>)createFilePathSet(childrenNames, /*caseSensitive: */ false);
      ioTime.addAndGet(System.nanoTime() - t);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("cached=" + cached + " actual=" + actualNames + " suspicious=" + wanted);
    }

    List<ChildInfo> newKids = wanted.isEmpty() ? List.of() : new ArrayList<>(wanted.size());
    for (String newName : wanted) {
      if (VfsUtil.isBadName(newName)) continue;
      FakeVirtualFile child = new FakeVirtualFile(dir, newName);
      FileAttributes attributes = getAttributes(fs, childrenWithAttributes, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, /*canonicalize: */ true));
      }
    }

    List<Pair<VirtualFile, FileAttributes>> existingMap = cached.isEmpty() ? List.of() : new ArrayList<>(cached.size());
    for (VirtualFile child : cached) {
      existingMap.add(new Pair<>(child, getAttributes(fs, childrenWithAttributes, child)));
    }

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
    var changed = ReadAction.computeBlocking(() -> !cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames()));
    vfsTime.addAndGet(System.nanoTime() - t);
    return changed;
  }

  /**
   * Converts a case-sensitive childrenWithAttributes map into case-insensitive, if toCaseSensitive=false,
   * leaves the map as-is otherwise
   */
  private static @NotNull Map<String, FileAttributes> adjustCaseSensitivity(@NotNull Map<String, FileAttributes> childrenWithAttributes,
                                                                            boolean toCaseSensitive) {
    if (toCaseSensitive) {
      return childrenWithAttributes;
    }
    else {
      Map<String, FileAttributes> childrenWithAttributesCaseInsensitive = createFilePathMap(
        childrenWithAttributes.size(),
        /*caseSensitive: */ false
      );
      childrenWithAttributesCaseInsensitive.putAll(childrenWithAttributes);
      if (childrenWithAttributesCaseInsensitive.size() != childrenWithAttributes.size()) {
        //TODO RC: seems like a conflict if dir.isCaseSensitive() is wrong/outdated (i.e. actual dir case-sensitivity
        //         is different from FS-default, and it wasn't yet determined).
        //         We should re-query dir.case-sensitivity
      }
      return childrenWithAttributesCaseInsensitive;
    }
  }

  private @Nullable FileAttributes getAttributes(@NotNull NewVirtualFileSystem fs,
                                                 @Nullable Map<String, FileAttributes> dirList,
                                                 @NotNull VirtualFile child) {
    FileAttributes attributes = null;
    if (dirList != null) {
      attributes = dirList.get(child.getName());
    }
    if (attributes == null && !(fs instanceof BatchingFileSystem)) {
      var t = System.nanoTime();
      attributes = computeAttributesForFile(fs, child);
      ioTime.addAndGet(System.nanoTime() - t);
    }
    return attributes;
  }

  /**
   * If attributes are computed in a cancellable context, then single-thread refresh gets a performance degradation.
   * The reason is {@link DiskQueryRelay#accessDiskWithCheckCanceled(Object)},
   * which starts constant exchanging messages with an IO thread.
   * The non-cancellable section here is merely a reification of the existing implicit assumption on cancellability,
   * so it does not make anything worse.
   * In the future, it should be removed in favor of non-blocking or suspending IO.
   */
  private static @Nullable FileAttributes computeAttributesForFile(NewVirtualFileSystem fs, VirtualFile file) {
    return Cancellation.computeInNonCancelableSection(() -> fs.getAttributes(file));
  }

  /** See {@link RefreshWorker#computeAttributesForFile(NewVirtualFileSystem, VirtualFile)} docs about cancellability */
  private static Map<String, FileAttributes> computeAllChildrenAttributes(@NotNull BatchingFileSystem fs,
                                                                          @NotNull VirtualFile dir,
                                                                          @Nullable Set<String> filter) {
    return Cancellation.computeInNonCancelableSection(() -> fs.listWithAttributes(dir, filter));
  }

  private ChildInfo childRecord(NewVirtualFileSystem fs, FakeVirtualFile child, FileAttributes attributes, boolean canonicalize) {
    var t = System.nanoTime();
    String name = canonicalize ? fs.getCanonicallyCasedName(child) : child.getName();
    boolean isEmptyDir = attributes.isDirectory() && !fs.hasChildren(child);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(child) : null;
    ioTime.addAndGet(System.nanoTime() - t);
    int nameId = vfsPeer.getNameId(name);
    return new ChildInfoImpl(nameId, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget, isEmptyDir);
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

  private static final class RefreshCancelledException extends RuntimeException {
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }

  private void checkCancelled(NewVirtualFile stopAt) throws RefreshCancelledException {
    Consumer<? super VirtualFile> testListener = ourTestListener;
    if (testListener != null) {
      testListener.accept(stopAt);
    }
    if (cancelled) {
      if (LOG.isTraceEnabled()) LOG.trace("cancelled at: " + stopAt);
      forceMarkDirty(stopAt);
      synchronized (this) {
        NewVirtualFile file;
        while ((file = refreshQueue.poll()) != null) {
          forceMarkDirty(file);
          semaphore.up();
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
    events.add(new VFileDeleteEvent(REQUESTOR, file));
  }

  private void scheduleCreation(List<VFileEvent> events, NewVirtualFile parent, String childName, FileAttributes attributes, @Nullable String symlinkTarget) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("create parent=" + parent + " name=" + childName + " attr=" + attributes);
    }

    ChildScanner.ScannedChildren scannedChildren = null;
    if (attributes.isDirectory() && !attributes.isSymLink()) {
      var t = System.nanoTime();
      scannedChildren = childScanner.scanChildrenRecursively(parent, childName);
      ioTime.addAndGet(System.nanoTime() - t);
    }

    ChildInfo[] children = scannedChildren == null ? null : scannedChildren.children();
    boolean childrenComplete = scannedChildren != null && scannedChildren.childrenComplete();
    events.add(new VFileCreateEvent(REQUESTOR, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, children, childrenComplete));

    VFileEvent caseSensitivityChangingEvent = ((PersistentFSImpl)persistentFS).determineCaseSensitivityAndPrepareUpdate(parent, childName);
    if (caseSensitivityChangingEvent != null) {
      events.add(caseSensitivityChangingEvent);
    }
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

    checkWritableAttributeChange(events, child, persistentFS.isWritable(child), childAttributes.isWritable());

    if (SystemInfoRt.isWindows) {
      checkHiddenAttributeChange(events, child, child.is(VFileProperty.HIDDEN), childAttributes.isHidden());
    }

    if (childAttributes.isSymLink()) {
      var t = System.nanoTime();
      var target = fs.resolveSymLink(child);
      ioTime.addAndGet(System.nanoTime() - t);
      checkSymbolicLinkChange(events, child, child.getCanonicalPath(), target);
    }

    if (!childAttributes.isDirectory()) {
      long oldTimestamp = persistentFS.getTimeStamp(child), newTimestamp = childAttributes.lastModified;
      long oldLength = persistentFS.getLastRecordedLength(child), newLength = childAttributes.length;
      if (oldTimestamp != newTimestamp || oldLength != newLength) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("update file=" + child +
                    (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
                    (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
        }

        events.add(new VFileContentChangeEvent(REQUESTOR, child, child.getModificationStamp(),
                                               VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH, oldTimestamp, newTimestamp,
                                               oldLength, newLength));
      }
      child.markClean();
    }
    else if (enqueue && isRecursive) {
      if (child instanceof VirtualDirectoryImpl) {
        semaphore.down();
        refreshQueue.add(child);
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
        ioTime.addAndGet(System.nanoTime() - t);
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
    events.add(new VFilePropertyChangeEvent(REQUESTOR, file, property, current, upToDate));
  }

  static Consumer<? super VirtualFile> ourTestListener;
}
