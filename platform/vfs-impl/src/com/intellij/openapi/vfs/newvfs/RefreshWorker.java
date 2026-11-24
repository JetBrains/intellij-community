// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshScanStats.WorkerStats;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.MathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FastUtilHashingStrategies;
import com.intellij.util.system.OS;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("SplitModeApiUsage")
final class RefreshWorker {
  private static final Logger LOG = Logger.getInstance(RefreshWorker.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  private static final int PARALLELISM = MathUtil.clamp(
    Registry.intValue("vfs.refresh.worker.parallelism", 6),
    1, Runtime.getRuntime().availableProcessors()
  );

  private static final Executor rawExecutor = ExecutorsKt.asExecutor(
      Dispatchers.getIO().limitedParallelism(PARALLELISM, "RefreshWorkerDispatcher")
  );
  /// Wraps [#rawExecutor] to propagate IntelliJ thread context to worker threads.
  private static final Executor executor = command -> rawExecutor.execute(
    AppScheduledExecutorService.capturePropagationAndCancellationContext(command)
  );

  private static final Object REQUESTOR = VFileEvent.REFRESH_REQUESTOR;

  private final boolean isRecursive;
  private final boolean parallel;

  private final Set<NewVirtualFile> roots;
  private final Queue<NewVirtualFile> refreshQueue;
  private final Semaphore semaphore;
  /// Receives each file this scan cleans: the file is 'actualizing' until the session applies its events
  private final InFlightScans.Batch scanned;
  /// Tells if a file must be scanned: a session decides that files it trusts (e.g. whether it trusts the 'actualizing' files)
  private final Predicate<NewVirtualFile> fileNeedsScan;

  private final PersistentFS persistentFS = PersistentFS.getInstance();
  private final FSRecordsImpl vfsPeer = ((PersistentFSImpl)persistentFS).peer();
  private final ChildScanner childScanner;

  private volatile boolean cancelled;

  /// The files whose file system could not answer in this scan. Their state is unknown, not absent: the worker generates
  /// no events for them and keeps them dirty, so a later refresh asks again once the file system can answer. See [#scan()].
  private final Set<NewVirtualFile> skipped = ConcurrentHashMap.newKeySet();

  // =========================== monitoring =========================================================================== //
  /// The counters of the worker thread that runs [#processQueue].
  private final ThreadLocal<WorkerStats> currentWorker = new ThreadLocal<>();
  private final TimeMeasuredFS timeMeasuredFS = new TimeMeasuredFS();

  RefreshWorker(Collection<NewVirtualFile> refreshRoots, boolean isRecursive) {
    this(refreshRoots, isRecursive, new InFlightScans().begin(), NewVirtualFile::isDirty);
  }

  RefreshWorker(
    Collection<NewVirtualFile> refreshRoots,
    boolean isRecursive,
    InFlightScans.Batch scanned,
    Predicate<NewVirtualFile> fileNeedsScan
  ) {
    this.isRecursive = isRecursive;
    this.scanned = scanned;
    this.fileNeedsScan = fileNeedsScan;
    parallel = isRecursive && PARALLELISM > 1 && !ApplicationManager.getApplication().isWriteIntentLockAcquired();
    roots = new HashSet<>(refreshRoots);
    refreshQueue = new LinkedBlockingQueue<>(refreshRoots);
    semaphore = new Semaphore(refreshRoots.size());
    childScanner = getChildScanner();
  }

  @SuppressWarnings("deprecation")
  private @NotNull ChildScanner getChildScanner() {
    if (!Registry.is("vfs.refresh.use.transient.files.for.children.preloading", true)) {
      return new NioChildScanner(file -> checkCancelled((NewVirtualFile)file));
    }
    if (!Registry.is("vfs.refresh.use.accurate.wfi.data.for.children.preloading", true)) {
      return new TransientChildScannerExcludeOnlyByUrl(file -> checkCancelled((NewVirtualFile)file));
    }
    return new TransientChildScanner(file -> checkCancelled((NewVirtualFile)file));
  }

  void cancel() {
    cancelled = true;
  }

  List<VFileEvent> scan() {
    var stats = new RefreshScanStats();
    try {
      var events = new ArrayList<VFileEvent>();
      if (!parallel) {
        singleThreadScan(events, stats);
      }
      else {
        parallelScan(events, stats);
      }
      // the parents are already clean, and `markDirty` of a still-dirty file does not reach them:
      // re-dirty the skipped files after the join, so the chain from the root to each of them is dirty again
      skipped.forEach(RefreshWorker::forceMarkDirty);
      return events;
    }
    finally {
      stats.finished();
      VfsUsageCollector.logRefreshScan(stats);
      if (stats.isSlow()) {
        LOG.info(stats.format());
      }
      else if (LOG.isDebugEnabled()) LOG.debug(stats.format());
    }
  }

  List<VFileEvent> scanNewFiles(@NotNull Map<NewVirtualFile, NewChildren> newFilesCaseSensitive) {
    var events = new ArrayList<VFileEvent>(newFilesCaseSensitive.size());
    try {
      for (var entry : newFilesCaseSensitive.entrySet()) {
        var parent = entry.getKey();
        var fs = parent.getFileSystem();
        var newChildren = entry.getValue();
        var requestor = newChildren.getRequestor();
        var file = newChildren.getFile();
        var childNames = CollectionFactory.createFilePathSet(newChildren.getChildren(), parent.isCaseSensitive());
        for (var childName : childNames) {
          if (cancelled) return events;
          if (VfsUtil.isBadName(childName)) continue;

          var fakeChild = new FakeVirtualFile(parent, childName);
          FileAttributes attributes;
          try {
            attributes = computeAttributesForFile(fs, fakeChild);
          }
          catch (FileStateUnknownException e) {
            THROTTLED_LOG.info("refresh skipped a new file, the FS can't tell its state: " + fakeChild + ": " + e.getMessage());
            continue;
          }
          if (attributes == null) continue;

          var canonicalName = fs.getCanonicallyCasedName(fakeChild);
          var symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(fakeChild) : null;
          scheduleCreationOrCopy(events, parent, canonicalName, attributes, symlinkTarget, file, requestor);
        }
      }
    }
    catch (RefreshCancelledException _) { }
    catch (CancellationException _) {
      cancelled = true;
    }
    catch (Throwable t) {
      LOG.error(t);
      cancelled = true;
    }
    return events;
  }

  private void singleThreadScan(List<VFileEvent> events, RefreshScanStats stats) {
    try {
      processQueue(events, stats);
    }
    catch (RefreshCancelledException e) {
      LOG.trace("refresh canceled [1T]");
    }
  }

  private void parallelScan(List<VFileEvent> events, RefreshScanStats stats) {
    var futures = new ArrayList<CompletableFuture<List<VFileEvent>>>(PARALLELISM);

    for (var i = 0; i < PARALLELISM; i++) {
      futures.add(CompletableFuture.supplyAsync(() -> {
        var threadEvents = new ArrayList<VFileEvent>();
        try {
          processQueue(threadEvents, stats);
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
      LOG.trace("refresh canceled [MT]");
    }
  }

  private void processQueue(/*OutParam*/ List<VFileEvent> events, RefreshScanStats stats) throws RefreshCancelledException {
    var worker = stats.newWorker();
    currentWorker.set(worker);
    worker.workerStarted();
    try {
      processQueue(events, worker);
    }
    finally {
      worker.workerFinished();
      currentWorker.remove();
    }
  }

  private void processQueue(/*OutParam*/ List<VFileEvent> events, WorkerStats worker) throws RefreshCancelledException {
    nextDir:
    while (!semaphore.isUp()) {
      worker.loopIterationStarted();
      var file = refreshQueue.poll();
      if (file == null) {
        TimeoutUtil.sleep(1);
        worker.loopIdled();
        continue;
      }

      try {
        var fs = file.getFileSystem();

        if (fs instanceof AsyncableFileSystem afs) {
          //can't refresh file that has async ops still in flight => flush them:
          try {
            timeMeasuredFS.fsync(afs, file);
          }
          catch (IOException e) {
            THROTTLED_LOG.info("refresh skipped, fsync failed: " + file + ": " + e.getMessage());
            skipped.add(file);
            continue;
          }
        }

        if (roots.contains(file)) {
          var attributes = rootAttributes(fs, file);
          if (attributes == null) {
            scheduleDeletion(events, file);
            markScanned(file);
            continue;
          }

          checkAndScheduleChildRefresh(events, fs, file.getParent(), file, attributes, false);

          if (!fileNeedsScan.test(file) || !file.isDirectory()) {
            continue;
          }
        }

        var dir = (VirtualDirectoryImpl)file;
        var mark = events.size();

        while (true) {
          checkCancelled(dir);
          var fullSync = dir.allChildrenLoaded();
          worker.dirRefreshStarted();
          try {
            boolean success;
            if (fullSync) {
              worker.fullScans++;
              success = fullDirRefresh(events, fs, dir);
            }
            else {
              worker.partialScans++;
              success = partialDirRefresh(events, fs, dir);
            }
            if (success) break;

            // the VFS changed during the scan of the directory: drop the events of the pass, and scan the directory again
            worker.dirRescanned();

            events.subList(mark, events.size()).clear();
            if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          }
          catch (InvalidVirtualFileAccessException e) {
            if (LOG.isDebugEnabled()) LOG.debug("invalid file access, skipping: " + dir, e);
            events.subList(mark, events.size()).clear();
            continue nextDir;
          }
        }
        worker.dirsDone++;

        if (isRecursive) {
          markScanned(dir);
        }
      }
      catch (FileStateUnknownException e) {
        THROTTLED_LOG.info("refresh skipped, the FS can't tell the state: " + file + ": " + e.getMessage());
        skipped.add(file);
      }
      finally {
        worker.loopFinished();
        semaphore.up();
      }
    }
  }

  private boolean fullDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) throws FileStateUnknownException {
    var snapshot = readAction(() -> {
      var children = dir.getChildren();
      return new Pair<>(children, getNames(children));
    });
    var vfsChildren = snapshot.first;
    var vfsNames = snapshot.second;

    var dirIsCaseSensitive = dir.isCaseSensitive();

    Map<String, FileAttributes> childrenWithAttributes;
    var unknownNames = CollectionFactory.createFilePathSet(/*expectedSize: */0, dirIsCaseSensitive);
    if (fs instanceof BatchingFileSystem batchingFileSystem) {
      childrenWithAttributes = timeMeasuredFS.listOrCheckByName(
        batchingFileSystem, dir, /*filter: */null, dirIsCaseSensitive,
        /* namesToCheck: */ vfsNames,
        /* unknownNames: */  unknownNames
      );
    }
    else {
      var childrenNames = timeMeasuredFS.list(fs, dir);
      childrenWithAttributes = CollectionFactory.createFilePathMap(childrenNames.length, dirIsCaseSensitive);
      for (var name : childrenNames) {
        childrenWithAttributes.put(name, null);
      }
      if (childrenWithAttributes.size() != childrenNames.length) {
        //TODO RC: seems like dir.isCaseSensitive() is wrong/outdated (i.e. actual dir case-sensitivity is different from
        //         FS-default, and it wasn't yet determined).
        //         We should re-query dir.case-sensitivity
      }
    }

    var newNames = CollectionFactory.createFilePathSet(childrenWithAttributes.keySet(), dirIsCaseSensitive);
    vfsNames.forEach(newNames::remove);

    var deletedNames = CollectionFactory.createFilePathSet(vfsNames, dirIsCaseSensitive);
    childrenWithAttributes.keySet().forEach(deletedNames::remove);
    unknownNames.forEach(deletedNames::remove);

    var actualNames = dirIsCaseSensitive ? null : new ObjectOpenCustomHashSet<>(
      childrenWithAttributes.keySet(), FastUtilHashingStrategies.getCaseInsensitiveStringStrategy()
    );
    if (LOG.isTraceEnabled()) {
      LOG.trace("current=" + vfsNames + " +" + newNames + " -" + deletedNames);
    }

    var newKids = newNames.isEmpty() && deletedNames.isEmpty() ? List.<ChildInfo>of() : new ArrayList<ChildInfo>(newNames.size());
    for (var newName : newNames) {
      if (VfsUtil.isBadName(newName)) continue;
      var child = new FakeVirtualFile(dir, newName);
      var attributes = getAttributes(fs, childrenWithAttributes, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, false));
      }
    }

    var existingMap = new ArrayList<Pair<VirtualFile, FileAttributes>>(vfsChildren.length - deletedNames.size());
    for (var child : vfsChildren) {
      if (!deletedNames.contains(child.getName()) && !unknownNames.contains(child.getName())) {
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

  private boolean isDirectoryChanged(VirtualDirectoryImpl dir, VirtualFile[] children, List<String> names) {
    return readAction(() -> {
      var currentChildren = dir.getChildren();
      return !Arrays.equals(children, currentChildren) || !names.equals(getNames(currentChildren));
    });
  }

  private static @Unmodifiable List<String> getNames(VirtualFile[] children) {
    return ContainerUtil.map(children, VirtualFile::getName);
  }

  private boolean partialDirRefresh(List<VFileEvent> events, NewVirtualFileSystem fs, VirtualDirectoryImpl dir) throws FileStateUnknownException {
    var snapshot = readAction(() -> new Pair<>(dir.getCachedChildren(), dir.getSuspiciousNames()));
    var cached = snapshot.first;
    var wanted = snapshot.second;

    var dirIsCaseSensitive = dir.isCaseSensitive();
    var namesToRefresh = CollectionFactory.createFilePathSet(wanted, dirIsCaseSensitive);
    for (var file : cached) namesToRefresh.add(file.getName());

    var childrenWithAttributes = (Map<String, FileAttributes>)null;
    var unknownNames = CollectionFactory.createFilePathSet(/*expectedSize: */ 0, dirIsCaseSensitive);
    if (fs instanceof BatchingFileSystem batchingFileSystem) {
      childrenWithAttributes = timeMeasuredFS.listOrCheckByName(
        batchingFileSystem, dir, namesToRefresh, dirIsCaseSensitive, /*namesToCheck: */namesToRefresh, unknownNames
      );
    }

    ObjectOpenCustomHashSet<String> actualNames;
    if (dirIsCaseSensitive || cached.isEmpty()) {
      actualNames = null;
    }
    else if (childrenWithAttributes != null) {
      actualNames = (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(childrenWithAttributes.keySet(), false);
    }
    else {
      var childrenNames = timeMeasuredFS.list(fs, dir);
      actualNames = (ObjectOpenCustomHashSet<String>)CollectionFactory.createFilePathSet(childrenNames, false);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("cached=" + cached + " actual=" + actualNames + " suspicious=" + wanted);
    }

    var newKids = wanted.isEmpty() ? List.<ChildInfo>of() : new ArrayList<ChildInfo>(wanted.size());
    for (var newName : wanted) {
      if (VfsUtil.isBadName(newName)) continue;
      var child = new FakeVirtualFile(dir, newName);
      var attributes = getAttributes(fs, childrenWithAttributes, child);
      if (attributes != null) {
        newKids.add(childRecord(fs, child, attributes, /*canonicalize:*/ true));
      }
    }

    var existingMap = cached.isEmpty() ? List.<Pair<VirtualFile, FileAttributes>>of() : new ArrayList<Pair<VirtualFile, FileAttributes>>(cached.size());
    for (var child : cached) {
      if (!unknownNames.contains(child.getName())) {
        existingMap.add(new Pair<>(child, getAttributes(fs, childrenWithAttributes, child)));
      }
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
    return readAction(() -> !cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames()));
  }

  private @Nullable FileAttributes getAttributes(
    NewVirtualFileSystem fs,
    @Nullable Map<String, FileAttributes> dirList,
    VirtualFile child
  ) throws FileStateUnknownException {
    FileAttributes attributes = null;
    if (dirList != null) {
      attributes = dirList.get(child.getName());
    }
    if (attributes == null && !(fs instanceof BatchingFileSystem)) {
      attributes = timeMeasuredFS.attrs(fs, child);
    }
    return attributes;
  }

  /// Gets the actual children of `dir`, to compare with the VFS children.
  /// The answer has 3 states for a name:
  /// - child exists: childName is in the result map;
  /// - child is unknown: childName is returned in `unknownNames`;
  /// - child is absent: not in result, not in `unknownNames`
  /// It is expected that the caller deletes the absent children from VFS -- but keeps the unknown children as-is.
  /// <p>
  /// A successful listing is returned as-is: [BatchingFileSystem#listWithAttributes] promises a complete listing, `unknownNames`
  /// remains empty.
  /// A failed listing does not prove that a child is absent -- e.g. it could be the user has no access to the dir listing, but has
  /// access to specific children -- so then the method checks each of `namesToCheck`, and adds `dir` to `skipped`.
  ///
  /// @param filter       the names to list, or null for all names
  /// @param namesToCheck the names to check one by one if the listing fails: the VFS children, and the wanted names of a partial scan
  /// @param unknownNames out: the names whose state is unknown. The caller generates no event for them
  /// @return the existing children with their attributes
  /// @throws FileSystemUnavailableException if the file system is unavailable
  private Map<String, FileAttributes> listOrCheckByName(
    BatchingFileSystem fs,
    VirtualDirectoryImpl dir,
    @Nullable Set<String> filter,
    boolean caseSensitive,
    Collection<String> namesToCheck,
    Set<String> unknownNames
  ) throws FileSystemUnavailableException {
    try {
      return adjustCaseSensitivity(computeAllChildrenAttributes(fs, dir, filter), caseSensitive);
    }
    catch (FileSystemUnavailableException e) {
      throw e;
    }
    catch (FileStateUnknownException e) {
      THROTTLED_LOG.info("refresh checks the children by name, the listing failed: " + dir + ": " + e.getMessage());
    }

    // without a listing, no new child appears, and the next refresh must try the listing again
    skipped.add(dir);
    var childrenWithAttributes = CollectionFactory.<FileAttributes>createFilePathMap(namesToCheck.size(), caseSensitive);
    for (var name : namesToCheck) {
      var child = new FakeVirtualFile(dir, name);
      try {
        // non-cancellable, see computeAttributesForFile()
        var attributes = Cancellation.computeInNonCancelableSection(() -> fs.getAttributesOrThrow(child));
        if (attributes != null) {
          childrenWithAttributes.put(name, attributes);
        }
      }
      catch (FileSystemUnavailableException e) {
        throw e;
      }
      catch (FileStateUnknownException e) {
        LOG.debug("refresh keeps a child, the file system cannot tell its state: " + child, e);
        unknownNames.add(name);
      }
    }
    return childrenWithAttributes;
  }

  /// Converts a case-sensitive childrenWithAttributes map into case-insensitive, if toCaseSensitive=false,
  /// leaves the map as-is otherwise.
  private static Map<String, FileAttributes> adjustCaseSensitivity(
    Map<String, FileAttributes> childrenWithAttributes,
    boolean toCaseSensitive
  ) {
    if (toCaseSensitive) {
      return childrenWithAttributes;
    }
    else {
      var childrenWithAttributesCaseInsensitive = CollectionFactory.<FileAttributes>createFilePathMap(childrenWithAttributes.size(), false);
      childrenWithAttributesCaseInsensitive.putAll(childrenWithAttributes);
      if (childrenWithAttributesCaseInsensitive.size() != childrenWithAttributes.size()) {
        //TODO RC: seems like a conflict if dir.isCaseSensitive() is wrong/outdated (i.e. actual dir case-sensitivity
        //         is different from FS-default, and it wasn't yet determined).
        //         We should re-query dir.case-sensitivity
      }
      return childrenWithAttributesCaseInsensitive;
    }
  }

  private ChildInfo childRecord(NewVirtualFileSystem fs, FakeVirtualFile child, FileAttributes attributes, boolean canonicalize) {
    var name = canonicalize ? timeMeasuredFS.canonicalName(fs, child) : child.getName();
    var isEmptyDir = attributes.isDirectory() && !timeMeasuredFS.hasChildren(fs, child);
    var symlinkTarget = attributes.isSymLink() ? timeMeasuredFS.symlink(fs, child) : null;
    var nameId = vfsPeer.getNameId(name);
    return new ChildInfoImpl(nameId, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget, isEmptyDir);
  }

  /// If attributes are computed in a cancellable context, then single-thread refresh gets a performance degradation.
  /// The reason is [DiskQueryRelay#accessDiskWithCheckCanceled(Object)],
  /// which starts constant exchanging messages with an IO thread.
  /// The non-cancellable section here is merely a reification of the existing implicit assumption on cancellability,
  /// so it does not make anything worse.
  /// In the future, it should be removed in favor of non-blocking or suspending IO.
  ///
  /// @throws FileStateUnknownException when the state of a file is unknown => the refresh should keep the file and keep it dirty, with
  /// no events, until the state become known
  private static @Nullable FileAttributes computeAttributesForFile(NewVirtualFileSystem fs, VirtualFile file) throws FileStateUnknownException {
    return Cancellation.computeInNonCancelableSection(
      () -> fs instanceof BatchingFileSystem bfs ? bfs.getAttributesOrThrow(file) : fs.getAttributes(file)
    );
  }

  /// See [RefreshWorker#computeAttributesForFile(NewVirtualFileSystem, VirtualFile)] docs about cancellability
  private static Map<String, FileAttributes> computeAllChildrenAttributes(
    BatchingFileSystem fs,
    VirtualFile dir,
    @Nullable Set<String> filter
  ) throws FileStateUnknownException {
    return Cancellation.computeInNonCancelableSection(() -> fs.listWithAttributes(dir, filter));
  }

  private void scheduleCreationOrCopy(
    List<VFileEvent> events,
    NewVirtualFile parent,
    String childName,
    FileAttributes attributes,
    @Nullable String symlinkTarget,
    @Nullable VirtualFile file,
    @Nullable Object requestor
  ) {
    if (LOG.isTraceEnabled()) {
      LOG.trace((file == null ? "create" : "copy") + " parent=" + parent + " name=" + childName + " attr=" + attributes);
    }

    var scannedChildren = (ChildScanner.ScannedChildren)null;
    if (attributes.isDirectory() && !attributes.isSymLink()) {
      scannedChildren = timeMeasuredFS.scanChildren(parent, childName);
    }

    var children = scannedChildren == null ? null : scannedChildren.children();
    var childrenComplete = scannedChildren != null && scannedChildren.childrenComplete();
    if (file == null) {
      events.add(new VFileCreateEvent(
        requestor, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, children, childrenComplete
      ));
    }
    else {
      events.add(new VFileCopyEvent(requestor, file, parent, childName, attributes, symlinkTarget, children, childrenComplete));
    }

    var caseSensitivityChangingEvent = ((PersistentFSImpl)persistentFS).determineCaseSensitivityAndPrepareUpdate(parent, childName);
    if (caseSensitivityChangingEvent != null) {
      events.add(caseSensitivityChangingEvent);
    }
  }

  private static void generateDeleteEvents(
    List<VFileEvent> events,
    VirtualDirectoryImpl dir,
    Set<String> deletedNames,
    ObjectOpenCustomHashSet<String> actualNames,
    List<ChildInfo> newKids
  ) {
    for (var name : deletedNames) {
      var child = dir.findChild(name);
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
    for (var record : newKids) {
      scheduleCreationOrCopy(
        events, dir, record.getName().toString(), record.getFileAttributes(), record.getSymlinkTarget(), null, REQUESTOR
      );
    }
  }

  private void generateUpdateEvents(
    List<VFileEvent> events,
    NewVirtualFileSystem fs,
    VirtualDirectoryImpl dir,
    ObjectOpenCustomHashSet<String> actualNames,
    List<Pair<VirtualFile, @Nullable FileAttributes>> existingMap
  ) {
    for (var pair : existingMap) {
      var child = (NewVirtualFile)pair.first;
      var childAttributes = pair.second;
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
    @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
    public Throwable fillInStackTrace() {
      return this;
    }
  }

  private void checkCancelled(NewVirtualFile stopAt) throws RefreshCancelledException {
    var testListener = ourTestListener;
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

  /// The file is added to the batch first, so it doesn't look 'clean' while some its changes (events) are still in flight
  private void markScanned(NewVirtualFile file) {
    scanned.add(file.getId());
    file.markClean();
  }

  private static void forceMarkDirty(NewVirtualFile file) {
    file.markClean();  // otherwise, consequent markDirty() won't have any effect
    file.markDirty();
  }

  private static void scheduleDeletion(List<VFileEvent> events, VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    events.add(new VFileDeleteEvent(REQUESTOR, file));
  }

  private void checkAndScheduleChildRefresh(
    List<VFileEvent> events,
    NewVirtualFileSystem fs,
    @Nullable NewVirtualFile parent,
    NewVirtualFile child,
    FileAttributes childAttributes,
    boolean enqueue
  ) {
    var fileNeedsScan = this.fileNeedsScan.test(child);
    if (LOG.isTraceEnabled()) LOG.trace("file=" + child + " needsScan=" + fileNeedsScan);
    if (!fileNeedsScan) {
      return;
    }

    if (checkAndScheduleFileTypeChange(events, fs, parent, child, childAttributes)) {
      markScanned(child);
      return;
    }

    checkWritableAttributeChange(events, child, persistentFS.isWritable(child), childAttributes.isWritable());

    if (OS.CURRENT == OS.Windows) {
      checkHiddenAttributeChange(events, child, child.is(VFileProperty.HIDDEN), childAttributes.isHidden());
    }

    if (childAttributes.isSymLink()) {
      var target = timeMeasuredFS.symlink(fs, child);
      checkSymbolicLinkChange(events, child, child.getCanonicalPath(), target);
    }

    if (!childAttributes.isDirectory()) {
      long oldTimestamp = persistentFS.getTimeStamp(child), newTimestamp = childAttributes.lastModified;
      long oldLength = persistentFS.getLastRecordedLength(child), newLength = childAttributes.length;
      if (oldTimestamp != newTimestamp || oldLength != newLength) {
        if (LOG.isTraceEnabled()) LOG.trace(
          "update file=" + child +
          (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
          (oldLength != newLength ? " len=" + oldLength + "->" + newLength : "")
        );

        events.add(new VFileContentChangeEvent(
          REQUESTOR, child, child.getModificationStamp(), VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH,
          oldTimestamp, newTimestamp, oldLength, newLength)
        );
      }
      markScanned(child);
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

  private boolean checkAndScheduleFileTypeChange(
    List<VFileEvent> events,
    NewVirtualFileSystem fs,
    @Nullable NewVirtualFile parent,
    NewVirtualFile child,
    FileAttributes childAttributes
  ) {
    boolean currentIsDirectory = child.isDirectory(), upToDateIsDirectory = childAttributes.isDirectory();
    boolean currentIsSymlink = child.is(VFileProperty.SYMLINK), upToDateIsSymlink = childAttributes.isSymLink();
    boolean currentIsSpecial = child.is(VFileProperty.SPECIAL), upToDateIsSpecial = childAttributes.isSpecial();

    var isFileTypeChanged = currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial;
    if (currentIsDirectory != upToDateIsDirectory || (isFileTypeChanged && !Boolean.getBoolean("refresh.ignore.file.type.changes"))) {
      scheduleDeletion(events, child);
      if (parent != null) {
        var symlinkTarget = upToDateIsSymlink ? timeMeasuredFS.symlink(fs, child) : null;
        scheduleCreationOrCopy(events, parent, child.getName(), childAttributes, symlinkTarget, null, REQUESTOR);
      }
      else {
        LOG.error("transgender orphan: " + child + ' ' + childAttributes);
      }
      return true;
    }

    return false;
  }

  /**
   * Returns the attributes of a refresh root, or {@code null} when the root does not exist.
   * A rename or a move changes the file in its file system before it changes the {@link VirtualFile}.
   * A lookup in between does not find the file by the path of the VFS.
   * A read action waits for the rename or the move, so the method looks the root up again when its path has changed.
   */
  private @Nullable FileAttributes rootAttributes(NewVirtualFileSystem fs, VirtualFile root) throws FileStateUnknownException {
    while (true) {
      var path = root.getPath();
      var attributes = timeMeasuredFS.attrs(fs, root);
      if (attributes != null) {
        return attributes;
      }
      var pathChanged = readAction(() -> root.isValid() && !root.getPath().equals(path));
      if (!pathChanged) {
        return null;
      }
    }
  }

  /// Runs the action under a blocking read action. Measures only the time spent waiting for the lock, not the action itself.
  private <T, E extends Throwable> T readAction(@NotNull ThrowableComputable<T, E> action) throws E {
    var t = RefreshScanStats.markNow();
    return ReadAction.computeBlocking(() -> {
      var worker = currentWorker.get();
      if (worker != null) {
        worker.readLockWaited(t);
      }
      return action.compute();
    });
  }

  private static boolean checkAndScheduleFileNameChange(
    List<VFileEvent> events,
    @Nullable ObjectOpenCustomHashSet<String> actualNames,
    VirtualFile child
  ) {
    if (actualNames != null) {
      var currentName = child.getName();
      var actualName = actualNames.get(currentName);
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
    var currentVfsTarget = currentTarget != null ? FileUtilRt.toSystemIndependentName(currentTarget) : null;
    if (!Objects.equals(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(events, child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  private static void scheduleAttributeChange(
    List<VFileEvent> events,
    VirtualFile file,
    @VirtualFile.PropName String property,
    Object current,
    Object upToDate
  ) {
    if (LOG.isTraceEnabled()) LOG.trace("update file=" + file + ' ' + property + '=' + current + "->" + upToDate);
    events.add(new VFilePropertyChangeEvent(REQUESTOR, file, property, current, upToDate));
  }

  static Consumer<? super VirtualFile> ourTestListener;

  /// The interface of the refresh to the FS. Each method makes one FS call and measures it:
  /// the time goes to the IO time and to the counters of the call kind, also when the call throws.
  private final class TimeMeasuredFS {
    void fsync(AsyncableFileSystem fs, VirtualFile file) throws IOException {
      measured(RefreshScanStats.FsKind.FSYNC, () -> {
        fs.fsync(file);
        return null;
      });
    }

    @Nullable FileAttributes attrs(NewVirtualFileSystem fs, VirtualFile file) throws FileStateUnknownException {
      return measured(RefreshScanStats.FsKind.ATTRS, () -> computeAttributesForFile(fs, file));
    }

    String[] list(NewVirtualFileSystem fs, VirtualFile dir) {
      return measured(RefreshScanStats.FsKind.LIST, () -> fs.list(dir));
    }

    @NotNull Map<String, FileAttributes> listOrCheckByName(
      @NotNull BatchingFileSystem fs,
      @NotNull VirtualDirectoryImpl dir,
      @Nullable Set<String> filter,
      boolean caseSensitive,
      @NotNull Collection<String> namesToCheck,
      @NotNull Set<String> unknownNames
    ) throws FileSystemUnavailableException {
      return measured(RefreshScanStats.FsKind.LIST, () -> RefreshWorker.this.listOrCheckByName(
        fs, dir, filter, caseSensitive, namesToCheck, unknownNames
      ));
    }

    String canonicalName(NewVirtualFileSystem fs, VirtualFile file) {
      return measured(RefreshScanStats.FsKind.CANONICAL, () -> fs.getCanonicallyCasedName(file));
    }

    boolean hasChildren(NewVirtualFileSystem fs, VirtualFile file) {
      return measured(RefreshScanStats.FsKind.HAS_CHILDREN, () -> fs.hasChildren(file));
    }

    String symlink(NewVirtualFileSystem fs, VirtualFile file) {
      return measured(RefreshScanStats.FsKind.SYMLINK, () -> fs.resolveSymLink(file));
    }

    ChildScanner.ScannedChildren scanChildren(NewVirtualFile parent, String childName) {
      return measured(RefreshScanStats.FsKind.SCAN_CHILDREN, () -> childScanner.scanChildrenRecursively(parent, childName));
    }

    private <T, E extends Throwable> T measured(RefreshScanStats.FsKind kind, @NotNull ThrowableComputable<T, E> action) throws E {
      var start = RefreshScanStats.markNow();
      var worker = currentWorker.get();
      if (worker == null) return action.compute();
      try {
        return action.compute();
      }
      finally {
        worker.fsCall(kind, start);
      }
    }
  }
}
