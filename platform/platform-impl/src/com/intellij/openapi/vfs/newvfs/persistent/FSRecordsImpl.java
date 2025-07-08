// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.IPersistentFSRecordsStorage.RecordReader;
import com.intellij.openapi.vfs.newvfs.persistent.IPersistentFSRecordsStorage.RecordUpdater;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.FileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.MRUFileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.SLRUFileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSInitializationResult;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.io.DataEnumerator.NULL_ID;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Facade of VFS persistence layer -- contains VFS storages and auxiliary objects.
 * <p>
 * Formerly {@link FSRecords} static methods served that role, now they are obsolete, and we transition to
 * use {@link FSRecordsImpl} _object_ for that.
 * Overarching plan is: for access VFS persistance one need to have {@link FSRecordsImpl} _object_, and not
 * rely on {@link FSRecords#getInstance()} or static {@link FSRecords} methods.
 * <p>
 * Still, {@link FSRecords} static helper will remain for a while supporting its current uses, and it also
 * simplifies transition with 'default' {@link FSRecords#getInstance() instance} of {@link FSRecordsImpl}.
 * We're getting rid of all those helpers step by step.
 * <p/>
 * At the end I plan to convert {@link FSRecordsImpl} a regular {@link com.intellij.openapi.components.Service},
 * with a usual .getInstance() method.
 */
@ApiStatus.Internal
public final class FSRecordsImpl implements Closeable {
  private static final Logger LOG = Logger.getInstance(FSRecordsImpl.class);

  //@formatter:off

  //MAYBE RC: do we need a async VFS flush now, when all VFS storages are memory-mapped? It seems like the only case there
  //          it could give us anything is an OS crash: if an OS crashes unexpectedly, async flush allows to save more of a VFS
  //          data. But a) OS crashes are not very often events b) VFS flush still doesn't save the whole VFS content in case
  //          of OS crash, and it is still quite a good chance either VFS or other IDE-critical on-disk structures get corrupted.
  //          So, maybe drop background VFS flush entirely?
  private static final boolean BACKGROUND_VFS_FLUSH = getBooleanProperty("vfs.flushing.use-background-flush", true);
  /** Not a lot of sense in gentle flusher for memory-mapped VFS storages */
  private static final boolean USE_GENTLE_FLUSHER = getBooleanProperty("vfs.flushing.use-gentle-flusher", false);


  /** Supported values: 'none', 'slru', 'mru' */
  private static final String NAME_CACHE_IMPL = System.getProperty("vfs.name-cache.impl", "mru");
  private static final boolean USE_FILE_NAME_CACHE = !"none".equals(NAME_CACHE_IMPL);
  private static final boolean USE_MRU_FILE_NAME_CACHE = "mru".equals(NAME_CACHE_IMPL);


  private static final String CONTENT_STORAGE_IMPL = System.getProperty("vfs.content-storage.impl", "over-mmapped-file");
  public static final boolean USE_CONTENT_STORAGE_OVER_NEW_FILE_PAGE_CACHE = "over-lock-free-page-cache".equals(CONTENT_STORAGE_IMPL);
  public static final boolean USE_CONTENT_STORAGE_OVER_MMAPPED_FILE = "over-mmapped-file".equals(CONTENT_STORAGE_IMPL);

  private static final String CONTENT_HASH_IMPL = System.getProperty("vfs.content-hash-storage.impl", "over-mmapped-file");
  public static final boolean USE_CONTENT_HASH_STORAGE_OVER_MMAPPED_FILE = "over-mmapped-file".equals(CONTENT_HASH_IMPL);

  /**
   * Cutoff for VFSContentStorage: file content larger than this threshold store with compression.
   * Range 4k-8k seems to be optimal, gauged by experiments on IntelliJ source tree:  with such thresholds only
   * ~7-15% files are actually compressed, but total size is just ~10-20% more than if all files were compressed.
   */
  public static final int COMPRESS_CONTENT_IF_LARGER_THAN = SystemProperties.getIntProperty("vfs.content-storage.compress-if-larger", 8000);

  /** Which compression to use for storing content: "zip" (java.util.zip), "lz4", "none" */
  public static final String COMPRESSION_ALGO = System.getProperty("vfs.content-storage-compression", "lz4");

  /**
   * Reuse fileIds deleted in a previous session.
   * It is a quite natural thing to do, but that reuse regularly presents itself in edge-case bugs, so we consider getting rid of it
   */
  public static final boolean REUSE_DELETED_FILE_IDS = getBooleanProperty("vfs.reuse-deleted-file-ids", false);

  //@formatter:on

  private static final FileAttribute SYMLINK_TARGET_ATTRIBUTE = new FileAttribute("FsRecords.SYMLINK_TARGET");


  /**
   * Default VFS error handler: marks VFS as corrupted, schedules rebuild on next app startup, and rethrows
   * the error passed in
   */
  public static final ErrorHandler ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD = (records, error) -> {
    if (!records.isClosed()) {
      records.connection.markAsCorruptedAndScheduleRebuild(error);
    }
    else {
      error.addSuppressed(records.alreadyClosedException());
    }
    if (error instanceof IOException ioException) {
      throw new UncheckedIOException(ioException);
    }
    ExceptionUtil.rethrow(error);
  };

  public static final ErrorHandler ON_ERROR_RETHROW = (__, error) -> {
    ExceptionUtil.rethrow(error);
  };

  public static ErrorHandler defaultErrorHandler() {
    return ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD;
  }


  private static int nextMask(int value, int bits, int prevMask) {
    assert value < (1 << bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }

  private static int nextMask(boolean value, int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }

  public static int currentImplementationVersion() {
    //bumped main version (63 -> 64) because AppendOnlyLog ids assignment algo changed
    int mainVFSFormatVersion = 64;
    //@formatter:off (nextMask better be aligned)
    return nextMask(mainVFSFormatVersion + (PersistentFSRecordsStorageFactory.storageImplementation().getId()), /* acceptable range is [0..255] */ 8,
           nextMask(!USE_CONTENT_STORAGE_OVER_MMAPPED_FILE,  //former USE_CONTENT_HASHES=true, this is why negation
           nextMask(IOUtil.useNativeByteOrderForByteBuffers(), // TODO RC: memory-mapped storages ignore that property
           nextMask(false, // former USE_ATTRIBUTES_OVER_NEW_FILE_PAGE_CACHE, free to re-use
           nextMask(true,  // former 'inline attributes', feel free to re-use
           nextMask(getBooleanProperty(FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER, false),
           nextMask(true,  // former USE_ATTRIBUTES_OVER_MMAPPED_FILE, free to re-use
           nextMask(true,  // former USE_SMALL_ATTR_TABLE, feel free to re-use
           nextMask(true,  // former PersistentHashMapValueStorage.COMPRESSION_ENABLED, feel free to re-use
           nextMask(false, // former FileSystemUtil.DO_NOT_RESOLVE_SYMLINKS, feel free to re-use
           nextMask(ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue(),
           nextMask(true,  // former USE_FAST_NAMES_IMPLEMENTATION, free to reuse
           nextMask(true   /* former USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION, free to reuse */, 0)))))))))))));
    //@formatter:on
  }

  /**
   * Factory
   *
   * @param storagesDirectoryPath directory there to put all FS-records files ('caches' directory)
   */
  public static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath) throws UncheckedIOException {
    return connect(storagesDirectoryPath, defaultErrorHandler());
  }

  public static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath,
                                      @NotNull ErrorHandler errorHandler) throws UncheckedIOException {
    if (IOUtil.isSharedCachesEnabled()) {
      //TODO RC: this has very little sense now: mmapped storages always use native byte order, regardless of this property
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
    }
    try {
      int currentVersion = currentImplementationVersion();
      VFSInitializationResult initializationResult = PersistentFSConnector.connect(
        storagesDirectoryPath,
        currentVersion
      );

      PersistentFSConnection connection = initializationResult.connection;

      Supplier<InvertedNameIndex> invertedNameIndexLazy = asyncFillInvertedNameIndex(connection.records());

      LOG.info("VFS initialized: " + NANOSECONDS.toMillis(initializationResult.totalInitializationDurationNs) + " ms, " +
               initializationResult.attemptsFailures.size() + " failed attempts, " +
               initializationResult.connection.recoveryInfo().recoveredErrors.size() + " error(s) were recovered");

      PersistentFSContentAccessor contentAccessor = new PersistentFSContentAccessor(connection);
      PersistentFSAttributeAccessor attributeAccessor = new PersistentFSAttributeAccessor(connection);
      PersistentFSRecordAccessor recordAccessor = new PersistentFSRecordAccessor(contentAccessor, attributeAccessor, connection);
      PersistentFSTreeAccessor treeAccessor = attributeAccessor.supportsRawAccess() ?
                                              new PersistentFSTreeRawAccessor(attributeAccessor, recordAccessor, connection) :
                                              new PersistentFSTreeAccessor(attributeAccessor, recordAccessor, connection);

      try {
        treeAccessor.ensureLoaded();

        return new FSRecordsImpl(
          connection,
          contentAccessor, attributeAccessor, treeAccessor, recordAccessor,
          invertedNameIndexLazy,
          currentVersion,
          errorHandler,
          initializationResult
        );
      }
      catch (Throwable e) {
        try {
          //ensure async task is finished:
          invertedNameIndexLazy.get();
        }
        catch (Throwable scanningEx) {
          e.addSuppressed(scanningEx);
        }

        try {
          connection.close();
        }
        catch (Throwable closingEx) {
          e.addSuppressed(closingEx);
        }
        LOG.error(e);//because we need more details
        //FIXME throw handleError(e) ?

        //noinspection InstanceofCatchParameter
        if (e instanceof Error) {
          throw (Error)e;
        }
        //noinspection InstanceofCatchParameter
        if (e instanceof RuntimeException) {
          throw (RuntimeException)e;
        }
        throw new UncheckedIOException((IOException)e);
      }
    }
    finally {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
    }
  }


  private final @NotNull PersistentFSConnection connection;
  private final @NotNull PersistentFSContentAccessor contentAccessor;
  private final @NotNull PersistentFSAttributeAccessor attributeAccessor;
  private final @NotNull PersistentFSTreeAccessor treeAccessor;
  private final @NotNull PersistentFSRecordAccessor recordAccessor;

  private final @NotNull ErrorHandler errorHandler;

  /** Additional information about how VFS was initialized */
  private final @NotNull VFSInitializationResult initializationResult;

  /**
   * Right now invertedNameIndex looks like a property of PersistentFSConnection -- but this is only because now it
   * operates with fileId/nameId. Future index impls may work with name hashes instead of nameId -- say, because hash
   * is a better way to identify strings if nameId is not unique. Such a version of index will require a name itself,
   * as String, which is less available inside PersistentFSConnection.
   */
  private final @NotNull Supplier<InvertedNameIndex> invertedNameIndexLazy;
  private final AtomicLong invertedNameIndexModCount = new AtomicLong();
  /** Statistics: how many times {@link #processFilesWithNames(Set, IntPredicate)} was called */
  private final AtomicLong invertedNameIndexRequestsServed = new AtomicLong();

  private final @Nullable Closeable flushingTask;

  /**
   * Caching wrapper around {@link PersistentFSConnection#namesEnumerator}
   * TODO RC: ideally this caching wrapper should be created inside connection, and connection.getNames()
   *          should return already wrapped (caching) enumerator -- so it is an implementation detail
   *          no one needs to know about
   */
  private final DataEnumeratorEx<String> fileNamesEnumerator;


  /** VFS implementation version */
  private final int currentVersion;


  /**
   * Lock to protect individual file-records updates.
   * <b>BEWARE</b>: FileRecordLock is not-reentrant (see it's docs), i.e. repeated attempt to lock in the same thread causes deadlock.
   * This property makes locking with this lock quite fragile and bad (leaky) abstraction -- which is why all the usages of this
   * lock should be contained in this class, otherwise it will very hard to ensure the correct usage.
   * <p>
   * Details of thread-safety:
   * <ul>
   * <li>All <b>read-only</b> accesses to file records, attributes, children must be done under {@link FileRecordLock#lockForRead(int)}.
   *   Special case: single-field reads from {@link PersistentFSRecordsStorage} (but not from attributes, and not from children!)
   *   could be done without the lock, because all fields accessors have volatile semantics -- this is an option for optimization.</li>
   * <li>All <b>write</b> accesses of file records, attributes must be done under {@link FileRecordLock#lockForWrite(int)} inside
   *   this class. {@link #updateRecordFields(int, RecordUpdater)} should be used by clients outside of this class.</li>
   * <li><b>Children</b> access has some specifics: read/write accesses to the children must be done under {@link FileRecordLock#lockForRead(int)}
   *   {@link FileRecordLock#lockForWrite(int)}, as usual (because currently children are stored in file attributes), but
   *   <b>read-modify-write</b> operations on children must be _additionally_ protected by {@link FileRecordLock#lockForHierarchyUpdate(int)}
   *   </li>
   * </ul>
   * Why dedicated 'hierarchyUpdateLock' was introduced: because read-modify-write updates on children could be quite long, and sometimes
   * even involve IO (see {@link #update(VirtualFile, int, Function, boolean)} method, and it's usages), while per-record accesses are
   * mostly short, so StampedLock could be used quite effectively. Protecting children read-modify-write ops with regular write lock
   * prevents concurrent reads, which is undesirable -- hence the trick.
   */
  private final FileRecordLock fileRecordLock = new FileRecordLock();

  /** Keep stacktrace of {@link #close()} call -- for better diagnostics of unexpected close */
  private volatile Exception closedStackTrace = null;

  //@GuardedBy("this")
  private final Set<AutoCloseable> closeables = new HashSet<>();
  private final CopyOnWriteArraySet<FileIdIndexedStorage> fileIdIndexedStorages = new CopyOnWriteArraySet<>();

  private FSRecordsImpl(@NotNull PersistentFSConnection connection,
                        @NotNull PersistentFSContentAccessor contentAccessor,
                        @NotNull PersistentFSAttributeAccessor attributeAccessor,
                        @NotNull PersistentFSTreeAccessor treeAccessor,
                        @NotNull PersistentFSRecordAccessor recordAccessor,
                        @NotNull Supplier<InvertedNameIndex> invertedNameIndexLazy,
                        int currentVersion,
                        @NotNull ErrorHandler errorHandler,
                        @NotNull VFSInitializationResult initializationResult) {
    this.connection = connection;
    this.contentAccessor = contentAccessor;
    this.attributeAccessor = attributeAccessor;
    this.treeAccessor = treeAccessor;
    this.recordAccessor = recordAccessor;
    this.errorHandler = errorHandler;
    this.invertedNameIndexLazy = invertedNameIndexLazy;

    this.currentVersion = currentVersion;
    this.initializationResult = initializationResult;

    //RC: this cache is actually a replacement for CachingEnumerator inside PersistentStringEnumerator.
    //    This inside-cache is disabled in VFS, and re-implemented on top.
    if (USE_FILE_NAME_CACHE) {
      FileNameCache cacheOverEnumerator = USE_MRU_FILE_NAME_CACHE ?
                                          new MRUFileNameCache(connection.names()) :
                                          new SLRUFileNameCache(connection.names());
      //cache.close() mostly just stops regular telemetry
      closeables.add(cacheOverEnumerator);
      this.fileNamesEnumerator = cacheOverEnumerator;
    }
    else {
      this.fileNamesEnumerator = connection.names();
    }

    if (BACKGROUND_VFS_FLUSH) {
      ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
      flushingTask = PersistentFSConnection.startFlusher(scheduler, connection, USE_GENTLE_FLUSHER);
    }
    else {
      flushingTask = null;
    }
  }

  //========== lifecycle: ========================================

  @Override
  public synchronized void close() {
    if (!connection.isClosed()) {
      LOG.info("VFS closing");
      Exception stackTraceEx = new Exception("FSRecordsImpl close stacktrace");

      if (flushingTask != null) {
        //Stop flushing _before_ assigning .close=true: otherwise there could be 'already closed' exceptions
        //  in Flusher -- mostly harmless, but annoying.
        try {
          flushingTask.close();
        }
        catch (Exception stoppingEx) {
          LOG.warn("Can't close VFS flushing task", stoppingEx);
          stackTraceEx.addSuppressed(stoppingEx);
        }
      }

      try {
        //ensure async scanning is finished -- until that records file is still in use,
        // which could cause e.g. problems with its deletion
        InvertedNameIndex invertedNameIndex = invertedNameIndexLazy.get();
        //Clear index is not required, but help GC by releasing huge table faster
        invertedNameIndex.clear();
      }
      catch (Throwable t) {
        LOG.warn("VFS: invertedNameIndex building is not terminated properly", t);
        stackTraceEx.addSuppressed(t);
      }

      for (AutoCloseable toClose : closeables) {
        try {
          toClose.close();
        }
        catch (Exception e) {
          LOG.warn("Can't close " + toClose, e);
          stackTraceEx.addSuppressed(e);
        }
      }

      try {
        connection.close();
      }
      catch (IOException e) {
        //handleError(e);
        stackTraceEx.addSuppressed(e);
      }

      //Assign at the end so all .addSuppressed() are visible.
      // Downside: it could be .closed=true but .closedStackTrace=null because not assigned yet.
      closedStackTrace = stackTraceEx;
    }
  }

  boolean isClosed() {
    return connection.isClosed();
  }

  void checkNotClosed() {
    if (connection.isClosed()) {
      throw alreadyClosedException();
    }
  }

  private @NotNull RuntimeException alreadyClosedException() {
    AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS is already closed (disposed)");
    if (closedStackTrace != null) {
      alreadyDisposed.addSuppressed(closedStackTrace);
    }

    return alreadyDisposed;
  }


  //========== general FS records properties: ========================================

  public int getVersion() {
    return currentVersion;
  }

  public long getCreationTimestamp() {
    checkNotClosed();
    try {
      return connection.creationTimestamp();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public VFSInitializationResult initializationResult() {
    return initializationResult;
  }

  //========== modifications counters: ========================================

  public long getInvertedNameIndexModCount() {
    return invertedNameIndexModCount.get();
  }

  @TestOnly
  int getPersistentModCount() {
    checkNotClosed();
    return connection.persistentModCount();
  }

  //========== FS records persistence: ========================================

  @TestOnly
  public void force() {
    checkNotClosed();
    try {
      connection.force();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @TestOnly
  public boolean isDirty() {
    checkNotClosed();
    return connection.isDirty();
  }

  //========== record allocations: ========================================

  @VisibleForTesting
  public int createRecord() {
    checkNotClosed();
    try {
      return recordAccessor.createRecord(fileIdIndexedStorages);
    }
    catch (Exception e) {
      throw handleError(e);
    }
  }

  /**
   * @return records (ids) freed in previous session, and not yet re-used in a current session.
   */
  @NotNull
  IntList getRemainFreeRecords() {
    checkNotClosed();
    return connection.freeRecords();
  }

  /**
   * @return records (ids) freed in current session.
   * Returns !empty list only in unit-tests -- outside of testing records freed in a current session are marked by REMOVED
   * flag, but not collected into free-list
   */
  @NotNull
  IntList getNewFreeRecords() {
    return recordAccessor.getNewFreeRecords();
  }

  void deleteRecordRecursively(int fileId) {
    checkNotClosed();
    try {
      markAsDeletedRecursively(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private void markAsDeletedRecursively(int fileId) throws IOException {
    IntList childrenIds = new IntArrayList();
    childrenIds.add(fileId);
    for (int i = 0; i < childrenIds.size(); i++) {
      int id = childrenIds.getInt(i);
      //FIXME RC: what if id is already deleted -> listIds(id) fails with 'attribute already deleted'?
      childrenIds.addElements(childrenIds.size(), listIds(id));
    }

    PersistentFSRecordsStorage records = connection.records();
    InvertedNameIndex invertedNameIndex = invertedNameIndexLazy.get();
    // delete children first:
    for (int i = childrenIds.size() - 1; i >= 0; i--) {
      int childId = childrenIds.getInt(i);
      //use 'update' lock even though 'read' lock would be enough -- but we don't have 'hierarchy read lock'
      long lockStamp = fileRecordLock.lockForWrite(childId);
      try {
        int nameId = records.getNameId(childId);
        int flags = records.getFlags(childId);

        if (PersistentFS.isDirectory(flags)) {
          treeAccessor.deleteDirectoryRecord(childId);
        }
        recordAccessor.markRecordAsDeleted(childId);

        invertedNameIndex.updateFileName(childId, NULL_NAME_ID, nameId);
      }
      finally {
        fileRecordLock.unlockForWrite(childId, lockStamp);
      }
    }
    invertedNameIndexModCount.incrementAndGet();
  }


  //========== FS roots manipulation: ========================================

  @VisibleForTesting
  public int @NotNull [] listRoots() {
    checkNotClosed();
    try {
      return withRecordReadLock(PersistentFSTreeAccessor.SUPER_ROOT_ID, treeAccessor::listRoots);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @VisibleForTesting
  public int findOrCreateRootRecord(@NotNull String rootUrl) {
    checkNotClosed();

    //use 'update' lock even though 'read' lock would be enough -- but we don't have 'hierarchy read lock'
    //MAYBE RC: Combine (lockForHierarchyUpdate + writeLock) into a single method, with some useless repetition removed?
    //          Not sure how much performance benefits it provides though
    fileRecordLock.lockForHierarchyUpdate(PersistentFSTreeAccessor.SUPER_ROOT_ID);
    try {
      return withRecordWriteLock(PersistentFSTreeAccessor.SUPER_ROOT_ID, () -> treeAccessor.findOrCreateRootRecord(rootUrl));
    }
    catch (Throwable t) {
      //not only IOException: almost everything thrown from .findOrCreateRootRecord() is a sign of VFS structure corruption
      throw handleError(t);
    }
    finally {
      fileRecordLock.unlockForHierarchyUpdate(PersistentFSTreeAccessor.SUPER_ROOT_ID);
    }
  }

  @VisibleForTesting
  public void forEachRoot(@NotNull ObjIntConsumer<? super String> rootConsumer) {
    forEachRoot((rootId, rootUrlId) -> {
      String rootUrl = getNameByNameId(rootUrlId);
      rootConsumer.accept(rootUrl, rootId);
      return true;
    });
  }

  @VisibleForTesting
  public void forEachRoot(@NotNull PersistentFSTreeAccessor.RootsConsumer rootConsumer) {
    checkNotClosed();

    try {
      //could be a long lock acquisition...
      withRecordReadLock(
        PersistentFSTreeAccessor.SUPER_ROOT_ID,
        () -> {
          treeAccessor.forEachRoot(rootConsumer);
          return null;
        }
      );
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void loadRootData(int fileId,
                    @NotNull String path,
                    @NotNull NewVirtualFileSystem fs) {
    try {
      treeAccessor.loadRootData(fileId, path, fs);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** Delete fileId from the roots catalog. Does NOT delete fileId record itself */
  void deleteRootRecord(int fileId) {
    fileRecordLock.lockForHierarchyUpdate(PersistentFSTreeAccessor.SUPER_ROOT_ID);
    try {
      withRecordWriteLock(
        PersistentFSTreeAccessor.SUPER_ROOT_ID,
        () -> {
          treeAccessor.deleteRootRecord(fileId);
          return null;
        }
      );
    }
    catch (IOException e) {
      throw handleError(e);
    }
    finally {
      fileRecordLock.unlockForHierarchyUpdate(PersistentFSTreeAccessor.SUPER_ROOT_ID);
    }
  }


  //========== directory/children manipulation: ========================================

  void loadDirectoryData(int id,
                         @NotNull VirtualFile parent,
                         @NotNull CharSequence path,
                         @NotNull NewVirtualFileSystem fs) {
    try {
      treeAccessor.loadDirectoryData(id, parent, path, fs);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean mayHaveChildren(int fileId) {
    try {
      StampedLock lock = fileRecordLock.lockFor(fileId);
      long readLockStamp = lock.readLock();
      try {
        return treeAccessor.mayHaveChildren(fileId);
      }
      finally {
        lock.unlockRead(readLockStamp);
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean wereChildrenAccessed(int fileId) {
    try {
      StampedLock lock = fileRecordLock.lockFor(fileId);
      long readLockStamp = lock.readLock();
      try {
        return treeAccessor.wereChildrenAccessed(fileId);
      }
      finally {
        lock.unlockRead(readLockStamp);
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public int @NotNull [] listIds(int fileId) {
    try {
      StampedLock lock = fileRecordLock.lockFor(fileId);
      long readLockStamp = lock.readLock();
      try {
        return treeAccessor.listIds(fileId);
      }
      finally {
        lock.unlockRead(readLockStamp);
      }
    }
    catch (IOException | IllegalArgumentException e) {
      throw handleError(e);
    }
  }

  /**
   * @return child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
   */
  public @NotNull ListResult list(int parentId) {
    try {
      return loadChildrenUnderRecordLock(parentId);
    }
    catch (IOException | IllegalArgumentException e) {
      throw handleError(e);
    }
  }


  public @Unmodifiable @NotNull List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, ChildInfo::getName);
  }

  /**
   * Performs the operation on children and save the modified children list atomically.
   * If setAllChildrenCached=true: sets {@link com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags#CHILDREN_CACHED}
   * flag on a parent record, if setAllChildrenCached=false does nothing additional (i.e. does NOT update CHILDREN_CACHED flag at all)
   */
  @NotNull
  @VisibleForTesting
  public ListResult update(@NotNull VirtualFile parent,
                           int parentId,
                           @NotNull Function<? super ListResult, ListResult> childrenConvertor,
                           boolean setAllChildrenCached) {
    SlowOperations.assertSlowOperationsAreAllowed();
    PersistentFSConnection.ensureIdIsValid(parentId);

    checkNotClosed();

    fileRecordLock.lockForHierarchyUpdate(parentId);
    try {
      ListResult children = loadChildrenUnderRecordLock(parentId);

      ListResult modifiedChildren = childrenConvertor.apply(children);

      // optimization: when converter returned unchanged children (see e.g. PersistentFSImpl.findChildInfo())
      // then do not save them back again unnecessarily
      if (!modifiedChildren.equals(children)) {
        //TODO RC: why we update symlinks here, under the lock?
        updateSymlinksForNewChildren(parent, children, modifiedChildren);

        saveChildrenUnderRecordLock(parentId, modifiedChildren, setAllChildrenCached);
      }
      else if (setAllChildrenCached) {
        StampedLock recordLock = fileRecordLock.lockFor(parentId);
        long stamp = recordLock.writeLock();
        try {
          connection.records().updateRecord(
            parentId,
            record -> record.addFlags(PersistentFS.Flags.CHILDREN_CACHED)
          );
        }
        finally {
          recordLock.unlockWrite(stamp);
        }
      }
      return modifiedChildren;
    }
    catch (CancellationException e) {
      // NewVirtualFileSystem.list methods CAN be interrupted now
      throw e;
    }
    catch (Throwable e) {
      throw handleError(e);
    }
    finally {
      fileRecordLock.unlockForHierarchyUpdate(parentId);
    }
  }

  void moveChildren(int fromParentId,
                    int toParentId) {
    assert fromParentId > 0 : fromParentId;
    assert toParentId > 0 : toParentId;

    checkNotClosed();

    if (fromParentId == toParentId) {
      return;
    }

    int minId = Math.min(fromParentId, toParentId);
    int maxId = Math.max(fromParentId, toParentId);
    fileRecordLock.lockForHierarchyUpdate(minId);
    try {
      fileRecordLock.lockForHierarchyUpdate(maxId);
      try {
        try {
          ListResult childrenToMove = loadChildrenUnderRecordLock(fromParentId);

          for (ChildInfo childToMove : childrenToMove.children) {
            int fileId = childToMove.getId();
            if (fileId == toParentId) {
              LOG.error("Cyclic parent/child relations");
              continue;
            }
            connection.records().setParent(fileId, toParentId);
          }

          saveChildrenUnderRecordLock(
            toParentId, childrenToMove,
            /*setAllChildrenCached: */ false
          );

          saveChildrenUnderRecordLock(
            fromParentId,
            new ListResult(getModCount(fromParentId), Collections.emptyList(), fromParentId),
            /*setAllChildrenCached: */ false
          );
        }
        catch (CancellationException e) {
          // NewVirtualFileSystem.list methods can be interrupted now
          throw e;
        }
        catch (Throwable e) {
          throw handleError(e);
        }
      }
      finally {
        fileRecordLock.unlockForHierarchyUpdate(maxId);
      }
    }
    finally {
      fileRecordLock.unlockForHierarchyUpdate(minId);
    }
  }

  /**
   * @param caseSensitivityAccessor must return true if a directory is case-sensitive, false otherwise
   *                                Supplier instead of just value because getting case-sensitivity may be costly (may require
   *                                access an underlying FS), but it is not always necessary, so better make it lazy
   */
  void moveChildren(@NotNull Supplier<Boolean> caseSensitivityAccessor,
                    int fromParentId,
                    int toParentId,
                    int childToMoveId) {
    assert fromParentId > 0 : fromParentId;
    assert toParentId > 0 : toParentId;

    checkNotClosed();

    if (fromParentId == toParentId) {
      return;
    }

    int minId = Math.min(fromParentId, toParentId);
    int maxId = Math.max(fromParentId, toParentId);
    fileRecordLock.lockForHierarchyUpdate(minId);
    try {
      fileRecordLock.lockForHierarchyUpdate(maxId);
      try {
        try {
          ListResult firstParentChildren = loadChildrenUnderRecordLock(fromParentId);
          ListResult fromParentChildrenWithoutChildMoved = firstParentChildren.remove(childToMoveId);
          if (fromParentChildrenWithoutChildMoved == firstParentChildren) {
            //RC: this means childToMove doesn't present among fromParent's children. It seems natural to fail move
            //    procedure in this case by throwing IllegalArgumentException, because there is definitely something
            //    wrong with arguments supplied. But the legacy version of this code didn't fail -- it proceeds
            //    with adding the childToMove to toParent's children, regardless of its current parent, if any.
            //    This seems error-prone to me, because we could easily end up with childToMove being in 2 parents'
            //    children lists -- so I decided to fail in this case:
            throw new IllegalArgumentException(
              "Can't move child(#" + childToMoveId + ") from parent(#" + fromParentId + ") to (#" + toParentId + "): " +
              "child doesn't belong to parent(#" + fromParentId + "), " +
              "child.parent(#" + connection.records().getParent(childToMoveId) + ")");
          }

          ListResult toParentChildren = loadChildrenUnderRecordLock(toParentId);

          // check that names are not duplicated:
          int childToMoveNameId = connection.records().getNameId(childToMoveId);
          ChildInfo alreadyExistingChild = findChild(caseSensitivityAccessor, toParentChildren, childToMoveNameId);
          if (alreadyExistingChild != null) {
            //RC: Again, the legacy version of this code just silently returned, but I prefer to throw IAE, since this
            //    is an error in params supplied, and it should be resolved
            String childToMoveName = getNameByNameId(childToMoveNameId);
            throw new IllegalArgumentException(
              "Can't move child(#" + childToMoveId + ", name='" + childToMoveName + "') " +
              "from parent(" + fromParentId + ") to (" + toParentId + "): " +
              "toParent already has a child with same name -- " + alreadyExistingChild);
          }

          ListResult toParentChildrenUpdated = toParentChildren.insert(
            new ChildInfoImpl(childToMoveId, childToMoveNameId, null, null, null)
          );

          connection.records().setParent(childToMoveId, toParentId);
          saveChildrenUnderRecordLock(fromParentId, fromParentChildrenWithoutChildMoved, /*setAllChildrenCached: */ false);
          saveChildrenUnderRecordLock(toParentId, toParentChildrenUpdated, /*setAllChildrenCached: */ false);
        }
        catch (CancellationException e) {
          // NewVirtualFileSystem.list methods can be interrupted now
          throw e;
        }
        catch (IllegalArgumentException e) {
          throw e;
        }
        catch (Throwable e) {
          throw handleError(e);
        }
      }
      finally {
        fileRecordLock.unlockForHierarchyUpdate(maxId);
      }
    }
    finally {
      fileRecordLock.unlockForHierarchyUpdate(minId);
    }
  }

  /** Reads children of parentId, under record-level read lock (not a hierarchy lock!) */
  private @NotNull ListResult loadChildrenUnderRecordLock(int parentId) throws IOException {
    StampedLock recordLock = fileRecordLock.lockFor(parentId);
    long stamp = recordLock.readLock();
    try {
      return treeAccessor.doLoadChildren(parentId);
    }
    finally {
      recordLock.unlockRead(stamp);
    }
  }

  /**
   * Saves children for parentId, under record-level write lock (not a hierarchy lock!).
   * If setAllChildrenCached=true, then sets CHILDREN_CACHED=true flag on a parent record,
   * if setAllChildrenCached=true does NOT change CHILDREN_CACHED flag in any way.
   */
  private void saveChildrenUnderRecordLock(int parentId,
                                           @NotNull ListResult modifiedChildren,
                                           boolean setAllChildrenCached) throws IOException {
    StampedLock recordLock = fileRecordLock.lockFor(parentId);
    long stamp = recordLock.writeLock();
    try {
      treeAccessor.doSaveChildren(parentId, modifiedChildren);

      if (setAllChildrenCached) {
        connection.records().updateRecord(
          parentId,
          record -> record.addFlags(PersistentFS.Flags.CHILDREN_CACHED)
        );
      }
    }
    finally {
      recordLock.unlockWrite(stamp);
    }
  }

  /**
   * @param caseSensitivityAccessor must return true if a directory is case-sensitive, false otherwise
   *                                Supplier instead of just value because getting case-sensitivity may be costly (may require access an underlying FS),
   *                                but it is not always necessary, so better make it lazy
   * @return child from a children list, with a name given by childNameId, with case sensitivity given by parent
   */
  private @Nullable ChildInfo findChild(@NotNull Supplier<Boolean> caseSensitivityAccessor,
                                        @NotNull ListResult children,
                                        int childNameId) {
    if (children.children.isEmpty()) {
      return null;
    }

    //fast path: lookup by nameId, which is an equivalent of case-sensitive name comparison:
    for (ChildInfo info : children.children) {
      if (childNameId == info.getNameId()) {
        return info;
      }
    }

    //if directory is !case-sensitive -- repeat lookup, now by actual name, with case-insensitive comparison:
    boolean caseSensitive = caseSensitivityAccessor.get();
    if (!caseSensitive) {
      String childName = getNameByNameId(childNameId);
      for (ChildInfo info : children.children) {
        if (Comparing.equal(childName, getNameByNameId(info.getNameId()), /* caseSensitive: */false)) {
          return info;
        }
      }
    }
    return null;
  }


  //========== symlink manipulation: ========================================

  @VisibleForTesting
  public void updateSymlinksForNewChildren(@NotNull VirtualFile parent,
                                           @NotNull ListResult oldChildren,
                                           @NotNull ListResult newChildren) {
    // find children which are added to the list and call updateSymlinkInfoForNewChild() on them (once)
    ContainerUtil.processSortedListsInOrder(
      oldChildren.children, newChildren.children,
      Comparator.comparingInt(ChildInfo::getId),
      /*mergeEqualItems: */ true,
      (childInfo, mergeResult) -> {
        if (mergeResult != ContainerUtil.MergeResult.COPIED_FROM_LIST1) {
          updateSymlinkInfoForNewChild(parent, childInfo);
        }
      });
  }

  private void updateSymlinkInfoForNewChild(@NotNull VirtualFile parent,
                                            @NotNull ChildInfo info) {
    int attributes = info.getFileAttributeFlags();
    if (attributes != -1 && PersistentFS.isSymLink(attributes)) {
      int id = info.getId();
      String symlinkTarget = info.getSymlinkTarget();
      storeSymlinkTarget(id, symlinkTarget);

      CharSequence name = info.getName();
      VirtualFileSystem fs = parent.getFileSystem();
      if (fs instanceof LocalFileSystemImpl) {
        String linkPath = parent.getPath() + '/' + name;
        ((LocalFileSystemImpl)fs).symlinkUpdated(id, parent, name, linkPath, symlinkTarget);
      }
    }
  }

  @Nullable
  String readSymlinkTarget(int fileId) {
    try (DataInputStream stream = readAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
      if (stream != null) {
        try {
          String result = StringUtil.nullize(IOUtil.readUTF(stream));
          return result == null ? null : FileUtil.toSystemIndependentName(result);
        }
        catch (EOFException eof) {
          //EA-822669: collect detailed info for debug (TODO: remove after root cause found)
          try (DataInputStream attrStream = readAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
            int size = attrStream.available();
            byte[] content = new byte[size];
            attrStream.readFully(content);
            throw handleError(
              new IOException("Can't read symLink from attribute[fileId:" + fileId + "][=" + IOUtil.toHexString(content) + "]", eof)
            );
          }
        }
      }

      return null;
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void storeSymlinkTarget(int fileId,
                          @Nullable String symlinkTarget) {
    checkNotClosed();
    try {
      try (DataOutputStream stream = writeAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
        IOUtil.writeUTF(stream, StringUtil.notNullize(symlinkTarget));
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  //========== file by name iterations: ========================================

  boolean processAllNames(@NotNull Processor<? super CharSequence> processor) {
    checkNotClosed();
    try {
      return connection.names().forEach((nameId, name) -> processor.process(name));
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean processFilesWithNames(@NotNull Set<String> names,
                                @NotNull IntPredicate processor) {
    checkNotClosed();

    if (names.isEmpty()) {
      return true;
    }

    try {
      IntList nameIds = new IntArrayList(names.size());
      for (String name : names) {
        int nameId = fileNamesEnumerator.tryEnumerate(name);
        if (nameId != NULL_NAME_ID) {
          nameIds.add(nameId);
        }
      }
      invertedNameIndexRequestsServed.incrementAndGet();
      return invertedNameIndexLazy.get().processFilesWithNames(nameIds, processor);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  //========== file record fields accessors: ========================================

  @PersistentFS.Attributes
  public int getFlags(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getFlags(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public boolean isDeleted(int fileId) {
    checkNotClosed();
    try {
      return recordAccessor.isDeleted(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public int getModCount(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getModCount(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  public int getParent(int fileId) {
    checkNotClosed();
    try {
      int parentId = connection.records().getParent(fileId);
      if (parentId == fileId) {
        throw new IllegalStateException("Cyclic parent child relations in the database: fileId = " + fileId + " == parentId");
      }

      return parentId;
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** Consider {@link #updateRecordFields(int, RecordUpdater)} for everything except for (probably) single-field updates */
  @ApiStatus.Obsolete
  @VisibleForTesting
  public void setParent(int fileId, int parentId) {
    if (fileId == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    checkNotClosed();
    try {
      connection.records().setParent(fileId, parentId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  /**
   * Registers name in file-name enumerator (='enumerates' the name), and return unique id
   * assigned by enumerator to that name.
   * This method changes VFS content (if name is a new one)!
   */
  public int getNameId(@NotNull String name) {
    checkNotClosed();
    try {
      return fileNamesEnumerator.enumerate(name);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  /** @return name by fileId, using FileNameCache */
  public @NotNull String getName(int fileId) {
    checkNotClosed();
    try {
      int nameId = connection.records().getNameId(fileId);
      return nameId == NULL_NAME_ID ? "" : fileNamesEnumerator.valueOf(nameId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public int getNameIdByFileId(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getNameId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** @return name from file-names enumerator (using cache) */
  public String getNameByNameId(int nameId) {
    assert nameId >= NULL_NAME_ID : "nameId(=" + nameId + ") must be positive";
    checkNotClosed();
    try {
      return nameId == NULL_NAME_ID ? "" : fileNamesEnumerator.valueOf(nameId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public void setName(int fileId, @NotNull String name) {
    checkNotClosed();

    int nameId = getNameId(name);

    updateRecordFields(fileId, record -> {
      int previousNameId = record.getNameId();
      if (previousNameId == nameId) {
        return false;
      }

      record.setNameId(nameId);

      invertedNameIndexLazy.get().updateFileName(fileId, nameId, previousNameId);
      invertedNameIndexModCount.incrementAndGet();
      return true;
    });
  }

  /** Consider {@link #updateRecordFields(int, RecordUpdater)} for everything except for (probably) single-field updates */
  @ApiStatus.Obsolete
  @VisibleForTesting
  public void setFlags(int fileId, @PersistentFS.Attributes int flags) {
    checkNotClosed();
    try {
      connection.records().setFlags(fileId, flags);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @VisibleForTesting
  public long getLength(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getLength(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** Consider {@link #updateRecordFields(int, RecordUpdater)} for everything except for (probably) single-field updates */
  @ApiStatus.Obsolete
  @VisibleForTesting
  public void setLength(int fileId, long len) {
    checkNotClosed();
    try {
      connection.records().setLength(fileId, len);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @VisibleForTesting
  public long getTimestamp(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getTimestamp(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** Consider {@link #updateRecordFields(int, RecordUpdater)} for everything except for (probably) single-field updates */
  @ApiStatus.Obsolete
  @VisibleForTesting
  public void setTimestamp(int fileId, long value) {
    checkNotClosed();
    try {
      connection.records().setTimestamp(fileId, value);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getContentRecordId(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getContentRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @VisibleForTesting
  public int getAttributeRecordId(int fileId) {
    checkNotClosed();
    try {
      return connection.records().getAttributeRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * @return nameId > 0
   */
  public int updateRecordFields(int fileId,
                                int parentId,
                                @NotNull FileAttributes attributes,
                                @NotNull String name,
                                boolean cleanAttributeRef) {
    checkNotClosed();

    int nameId = getNameId(name);
    long timestamp = attributes.lastModified;
    long length = attributes.isDirectory() ? -1L : attributes.length;
    int flags = PersistentFSImpl.fileAttributesToFlags(attributes);

    InvertedNameIndex filenameIndex = invertedNameIndexLazy.get();
    updateRecordFields(fileId, record -> {
      record.setParent(parentId);
      record.setNameId(nameId);
      record.setFlags(flags);
      if (cleanAttributeRef) {
        record.setAttributeRecordId(NULL_ID);
      }
      record.setTimestamp(timestamp);
      record.setLength(length);

      filenameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
      return true;
    });

    invertedNameIndexModCount.incrementAndGet();

    return nameId;
  }


  // experimental API for fine-grained-locked file records access:

  //TODO RC: specify exactly what can and can't be done in reader/writer lambdas

  int updateRecordFields(int fileId, @NotNull RecordUpdater updater) {
    checkNotClosed();

    PersistentFSRecordsStorage fileRecords = connection.records();
    long lockStamp = fileRecordLock.lockForWrite(fileId);
    try {
      return fileRecords.updateRecord(fileId, updater);
      //TODO RC: it would be better to get 'modified' flag here, but updateRecords() doesn't provide the 'modified' property
      // -- because it returns recordId instead.
      // Now, the only reason for returning recordId is to cover the cases with new record inserted. But this is not so much needed:
      // we could insert new record with .allocateRecords(), and then update its fields with .updateRecordFields() -- there is no
      // concurrency issue with it, because newly allocated id is not yet published, so no one else could access it (except for iterating
      // through VFS with for(fileId in 0..maxId) -- which is a very low-level access anyway). So, maybe change the fileRecords.updateRecord()
      // semantics so that it returns 'modified' property instead of newRecordId?
    }
    catch (IOException ex) {
      throw handleError(ex);
    }
    finally {
      fileRecordLock.unlockForWrite(fileId, lockStamp);
    }
  }

  <R> R readRecordFields(int fileId, @NotNull RecordReader<R> reader) {
    checkNotClosed();

    IPersistentFSRecordsStorage fileRecords = connection.records();
    long lockStamp = fileRecordLock.lockForRead(fileId);
    try {
      return fileRecords.readRecord(fileId, reader);
    }
    catch (IOException ex) {
      throw handleError(ex);
    }
    finally {
      fileRecordLock.unlockForRead(fileId, lockStamp);
    }
  }

  <R> R readRecordFieldsOptimistic(int fileId, @NotNull RecordReader<R> reader) {
    checkNotClosed();

    IPersistentFSRecordsStorage fileRecords = connection.records();

    StampedLock lock = fileRecordLock.lockFor(fileId);
    long lockStamp = lock.tryOptimisticRead();
    try {
      for (; ; lockStamp = lock.readLock()) {
        if (lockStamp == 0L) {
          continue;
        }

        // possibly racy reads
        R result = fileRecords.readRecord(fileId, reader);

        if (!lock.validate(lockStamp)) {
          continue;
        }
        return result;
      }
    }
    catch (IOException ex) {
      throw handleError(ex);
    }
    finally {
      if (StampedLock.isReadLockStamp(lockStamp)) {
        lock.unlockRead(lockStamp);
      }
    }
  }


  //========== file attributes accessors: ========================================

  //About locking for attribute storage:
  // 1. Speculative lock.tryOptimisticRead() is not applicable, since attribute record is not just a set of (volatile) fields,
  //    it is variable-size record, hence full-fledged read lock needed.
  // 2. The way we use locking to protect attribute storage access here is coupled to the details of attribute storage implementation.
  //    E.g. we know that appending a new record to the storage is lock-free (just atomic increment of a cursor) => we could use
  //    lock segmented by fileId to protect read, and write, and _append_. Without atomic append a global lock would be needed here.
  //    E.g. we assume here that inputStreams and outputStream provided by attributes storage are both byte[]-backed, so there is
  //    no need to protect the _use_ of the in/out streams with locks -- only creation, and (for a write stream) a .close() method,
  //    which commits changes to the storage.
  //    So beware: if you change the attribute storage implementation, re-view the locking below


  @VisibleForTesting
  public @Nullable AttributeInputStream readAttribute(int fileId, @NotNull FileAttribute attribute) {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long lockStamp = lock.readLock();
    try {
      return attributeAccessor.readAttribute(fileId, attribute);
    }
    catch (IOException e) {
      throw handleError(e);
    }
    finally {
      lock.unlockRead(lockStamp);
    }
  }

  @VisibleForTesting
  public @NotNull AttributeOutputStream writeAttribute(int fileId, @NotNull FileAttribute attribute) {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long lockStamp = lock.writeLock();
    try {
      AttributeOutputStream stream = attributeAccessor.writeAttribute(fileId, attribute);
      //AttributeOutputStream is byte[]-backed stream that commits the changes in .close() method
      // Create a delegating stream: overwrite .close() and protect it with a write lock:
      return new AttributeOutputStream(stream) {
        @Override
        public void writeEnumeratedString(String str) throws IOException {
          stream.writeEnumeratedString(str);
        }

        @Override
        public void close() throws IOException {
          long lockStamp = lock.writeLock();
          try {
            super.close();
          }
          catch (FileTooBigException e) {
            LOG.warn("Error storing " + attribute + " of file(" + fileId + ")", e);
            //don't mark VFS as corrupted, error is due to data supplied from outside
            throw e;
          }
          catch (Throwable t) {
            LOG.warn("Error storing " + attribute + " of file(" + fileId + ")", t);
            throw handleError(t);
          }
          finally {
            lock.unlockWrite(lockStamp);
          }
        }
      };
    }
    finally {
      lock.unlockWrite(lockStamp);
    }
  }

  //'raw' (lambda + ByteBuffer instead of Input/OutputStream) attributes access: experimental

  @ApiStatus.Internal
  boolean supportsRawAttributesAccess() {
    return attributeAccessor.supportsRawAccess();
  }

  @ApiStatus.Internal
  <R> @Nullable R readAttributeRaw(int fileId,
                                   @NotNull FileAttribute attribute,
                                   @NotNull ByteBufferReader<R> reader) {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long lockStamp = lock.readLock();
    try {
      return attributeAccessor.readAttributeRaw(fileId, attribute, reader);
    }
    catch (IOException e) {
      throw handleError(e);
    }
    finally {
      lock.unlockRead(lockStamp);
    }
  }

  @ApiStatus.Internal
  void writeAttributeRaw(int fileId,
                         @NotNull FileAttribute attribute,
                         @NotNull ByteBufferWriter writer) {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long lockStamp = lock.writeLock();
    try {
      attributeAccessor.writeAttributeRaw(fileId, attribute, writer);
    }
    finally {
      lock.unlockWrite(lockStamp);
    }
  }


  //========== file content accessors: ========================================

  @VisibleForTesting
  public @Nullable InputStream readContent(int fileId) {
    try {
      return contentAccessor.readContent(fileId);
    }
    catch (InterruptedIOException ie) {
      //RC: goal is to just bypass handleError(), which likely marks VFS corrupted,
      //    but thread interruption during _read_ doesn't corrupt anything
      throw new RuntimeException(ie);
    }
    catch (OutOfMemoryError oom) {
      throw oom;
    }
    catch (ZipException e) {
      // we use zip to compress content
      String fileName = getName(fileId);
      long length = getLength(fileId);
      IOException diagnosticException = new IOException(
        "Failed to decompress file's content for file. File name = " + fileName + ", length = " + length);
      diagnosticException.addSuppressed(e);
      throw handleError(diagnosticException);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  @NotNull
  InputStream readContentById(int contentId) {
    try {
      return contentAccessor.readContentByContentId(contentId);
    }
    catch (InterruptedIOException ie) {
      //RC: goal is to just not go into handleError(), which likely marks VFS corrupted,
      //    but thread interruption during _read_ doesn't corrupt anything
      throw new RuntimeException(ie);
    }
    catch (OutOfMemoryError oom) {
      throw oom;
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  int acquireFileContent(int fileId) {
    try {
      return contentAccessor.acquireContentRecord(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void releaseContent(int contentId) {
    try {
      contentAccessor.releaseContentRecord(contentId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @NotNull
  @VisibleForTesting
  public DataOutputStream writeContent(int fileId, boolean fixedSize) {
    return new DataOutputStream(contentAccessor.new ContentOutputStream(fileId, fixedSize)) {
      @Override
      public void close() {
        try {
          super.close();
        }
        catch (IOException e) {
          throw handleError(e);
        }
      }
    };
  }

  @VisibleForTesting
  public void writeContent(int fileId, @NotNull ByteArraySequence bytes, boolean fixedSize) {
    try {
      contentAccessor.writeContent(fileId, bytes, fixedSize);
    }
    //TODO RC: catch and rethrow InterruptedIOException & OoMError as in readContent(),
    //         thus bypassing handleError() and VFS rebuild. But I'm not sure that writeContent
    //         is really safe against thread-interruption/OoM: i.e. it could be InterruptedException
    //         or OoM really left RefCountingContentStorage in a inconsistent state -- more
    //         thoughtful analysis (and likely a tests!) needed
    catch (Throwable t) {
      throw handleError(t);
    }
  }

  @TestOnly
  byte[] getContentHash(int fileId) {
    try {
      return contentAccessor.getContentHash(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * Stores content and return contentRecordId, by which content could be later retrieved.
   * If the same content (bytes) was already stored -- method could return id of already existing record, without allocating
   * & storing new record.
   */
  int writeContentRecord(@NotNull ByteArraySequence content) throws ContentTooBigException {
    try {
      return contentAccessor.writeContentRecord(content);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  //========== aux: ========================================

  private <T, E extends Exception> T withRecordReadLock(int fileId,
                                                        @NotNull ThrowableComputable<T, E> lambda) throws E {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long stamp = lock.readLock();
    try {
      return lambda.compute();
    }
    finally {
      lock.unlockRead(stamp);
    }
  }

  private <T, E extends Exception> T withRecordWriteLock(int fileId,
                                                         @NotNull ThrowableComputable<T, E> lambda) throws E {
    StampedLock lock = fileRecordLock.lockFor(fileId);
    long stamp = lock.writeLock();
    try {
      return lambda.compute();
    }
    finally {
      lock.unlockWrite(stamp);
    }
  }

  /**
   * This method creates 'VFS rebuild marker', which forces VFS to rebuild on the next startup.
   * If cause argument is not null -- this scenario is considered an 'error', and warnings are
   * logged, if cause is null -- the scenario is considered not an 'error', but a regular
   * request -- e.g. no errors logged.
   */
  public void scheduleRebuild(@Nullable String diagnosticMessage,
                              @Nullable Throwable cause) {
    checkNotClosed();
    connection.scheduleVFSRebuild(diagnosticMessage, cause);
  }

  /**
   * Mark VFS to defragment on next restart.
   * Currently, defragmentation implementation == rebuild.
   * The difference between this method and {@linkplain #scheduleRebuild(String, Throwable)} is that this method is not about
   * 'rebuild VFS because it is corrupted', but 'defragment VFS because it may contain al lot of garbage' -- this is why there
   * is no 'message' nor 'errorCause' parameters.
   */
  public void scheduleDefragmentation() throws IOException {
    connection.scheduleDefragmentation();
  }

  /**
   * Method is supposed to be called in a pattern like this:
   * <pre>
   * try{
   *  ...
   * }
   * catch(Throwable t){
   *   throw handleError(e);
   * }
   * </pre>
   * i.e. in a 'throw' statement -- to make clear, it will throw an exception. Method declared to
   * return RuntimeException specifically for that purpose: to be used in a 'throw' statement, so
   * the javac understands it is as a method exit point.
   */
  @Contract("_->fail")
  RuntimeException handleError(Throwable e) throws RuntimeException, Error {
    if (e instanceof ClosedStorageException || isClosed()) {
      // no connection means IDE is closing...
      RuntimeException alreadyDisposed = alreadyClosedException();
      alreadyDisposed.addSuppressed(e);
      throw alreadyDisposed;
    }
    if (e instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)e;
    }

    errorHandler.handleError(this, e);

    //errorHandler.handleError() _must_ throw some exception:
    throw new AssertionError("Bug: should be unreachable, since ErrorHandle must throw some exception", e);
  }


  /** Adds an object which must be closed during VFS close process */
  public synchronized void addCloseable(@NotNull Closeable closeable) {
    checkNotClosed();
    closeables.add(closeable);
  }

  /**
   * Registers a storage keeping some data by fileId.
   * Since we reuse fileId of removed files, we need to be sure all data attached to the re-used fileId was
   * cleaned before re-use -- hence a storage that keeps such data should implement {@link FileIdIndexedStorage}
   * interface, and should be registered with that method (or invent own method to keep track of removed files)
   */
  public void addFileIdIndexedStorage(@NotNull FileIdIndexedStorage storage) {
    fileIdIndexedStorages.add(storage);
  }


  //========== diagnostic, sanity checks: ========================================

  @TestOnly
  void checkFilenameIndexConsistency() {
    invertedNameIndexLazy.get().checkConsistency();
  }

  public int corruptionsDetected() {
    return connection.corruptionsDetected();
  }

  public long invertedNameIndexRequestsServed() {
    return invertedNameIndexRequestsServed.get();
  }

  //========== accessors for diagnostics & sanity checks: ========================

  public PersistentFSConnection connection() {
    return connection;
  }

  PersistentFSContentAccessor contentAccessor() {
    return contentAccessor;
  }

  @VisibleForTesting
  public PersistentFSAttributeAccessor attributeAccessor() {
    return attributeAccessor;
  }

  @VisibleForTesting
  public PersistentFSTreeAccessor treeAccessor() {
    return treeAccessor;
  }

  @VisibleForTesting
  public PersistentFSRecordAccessor recordAccessor() {
    return recordAccessor;
  }

  @VisibleForTesting
  public static @NotNull Supplier<@NotNull InvertedNameIndex> asyncFillInvertedNameIndex(@NotNull PersistentFSRecordsStorage recordsStorage) {
    CompletableFuture<InvertedNameIndex> fillUpInvertedNameIndexTask = PersistentFsConnectorHelper.INSTANCE.executor().async(() -> {
      InvertedNameIndex invertedNameIndex = new InvertedNameIndex();
      // fill up nameId->fileId index:
      int maxAllocatedID = recordsStorage.maxAllocatedID();
      for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
        int flags = recordsStorage.getFlags(fileId);
        int nameId = recordsStorage.getNameId(fileId);
        if (!hasDeletedFlag(flags) && nameId != NULL_NAME_ID) {
          invertedNameIndex.updateDataInner(fileId, nameId);
        }
      }
      LOG.info("VFS scanned: file-by-name index was populated");
      return invertedNameIndex;
    });

    // We don't need volatile/atomicLazy, since computation is idempotent: same instance returned always.
    // So _there could be_ a data race, but it is a benign race.
    return () -> {
      try {
        return fillUpInvertedNameIndexTask.join();
      }
      catch (Throwable e) {
        throw new IllegalStateException("Lazy invertedNameIndex computation is failed", e);
      }
    };
  }

  public interface ErrorHandler {

    /**
     * Called when some of FSRecords method encounters an exception, it doesn't know how to process by itself.
     * The method should throw an exception: either rethrow original exception, or wrap it into something more
     * appropriate -- but could also have side effects: e.g. schedule VFS rebuild is the most obvious,
     * 'traditional' way of handling errors.
     */
    void handleError(@NotNull FSRecordsImpl records,
                     @NotNull Throwable error) throws Error, RuntimeException;
  }

  /**
   * Any storage keeping some data by fileId.
   * Since we reuse fileId of removed files, we need to be sure all data attached to the re-used fileId was
   * cleaned before re-use -- hence every storage that keeps such data should implement this interface, and
   * should be registered {@link #addFileIdIndexedStorage(FileIdIndexedStorage)}
   */
  public interface FileIdIndexedStorage {
    void clear(int fileId) throws IOException;
  }
}
