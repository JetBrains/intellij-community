// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.FileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.MRUFileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.SLRUFileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSInitializationResult;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.serviceContainer.ContainerUtilKt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static com.intellij.util.SystemProperties.getBooleanProperty;
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

  private static final boolean BACKGROUND_VFS_FLUSH = getBooleanProperty("vfs.flushing.use-background-flush", true);
  private static final boolean USE_GENTLE_FLUSHER = getBooleanProperty("vfs.flushing.use-gentle-flusher", false);

  public static final boolean USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION = getBooleanProperty("vfs.attributes-storage.streamlined", true);
  /** Supported values: 'over-old-page-cache', 'over-lock-free-page-cache', 'over-mmapped-file'... */
  private static final String ATTRIBUTES_STORAGE_IMPL = System.getProperty("vfs.attributes-storage.impl", "over-mmapped-file");
  public static final boolean USE_ATTRIBUTES_OVER_NEW_FILE_PAGE_CACHE = "over-lock-free-page-cache".equals(ATTRIBUTES_STORAGE_IMPL);
  public static final boolean USE_ATTRIBUTES_OVER_MMAPPED_FILE = "over-mmapped-file".equals(ATTRIBUTES_STORAGE_IMPL);
  
  public static final boolean USE_RAW_ACCESS_TO_READ_CHILDREN = getBooleanProperty("vfs.use-raw-access-to-read-children", true);

  public static final boolean USE_FAST_NAMES_IMPLEMENTATION = getBooleanProperty("vfs.use-fast-names-enumerator", true);

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

  /**
   * Wrap {@link AlreadyDisposedException} in {@link ProcessCanceledException} if under progress indicator or Job.
   * See containerUtil.isUnderIndicatorOrJob()
   */
  private static final boolean WRAP_ADE_IN_PCE = getBooleanProperty("vfs.wrap-ade-in-pce", true);
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
    if (error instanceof IOException) {
      throw new UncheckedIOException((IOException)error);
    }
    ExceptionUtil.rethrow(error);
  };

  public static final ErrorHandler ON_ERROR_RETHROW = (__, error) -> {
    ExceptionUtil.rethrow(error);
  };

  public static ErrorHandler getDefaultErrorHandler() {
    return ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD;
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


  private final FileRecordLock updateLock = new FileRecordLock();

  private volatile boolean closed = false;

  /** Keep stacktrace of {@link #close()} call -- for better diagnostics of unexpected close */
  private volatile Exception closedStackTrace = null;

  //@GuardedBy("this")
  private final Set<AutoCloseable> closeables = new HashSet<>();
  private final CopyOnWriteArraySet<FileIdIndexedStorage> fileIdIndexedStorages = new CopyOnWriteArraySet<>();

  private static int nextMask(int value,
                              int bits,
                              int prevMask) {
    assert value < (1 << bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }

  private static int nextMask(boolean value,
                              int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }

  public static int currentImplementationVersion() {
    //bumped main version (63 -> 64) because AppendOnlyLog ids assignment algo changed
    final int mainVFSFormatVersion = 64;
    //@formatter:off (nextMask better be aligned)
    return nextMask(mainVFSFormatVersion + (PersistentFSRecordsStorageFactory.storageImplementation().getId()), /* acceptable range is [0..255] */ 8,
           nextMask(!USE_CONTENT_STORAGE_OVER_MMAPPED_FILE,  //former USE_CONTENT_HASHES=true, this is why negation
           nextMask(IOUtil.useNativeByteOrderForByteBuffers(),
           nextMask(PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED && USE_ATTRIBUTES_OVER_NEW_FILE_PAGE_CACHE,//pageSize was changed on old<->new transition
           nextMask(true,  // former 'inline attributes', feel free to re-use
           nextMask(getBooleanProperty(FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER, false),
           nextMask(USE_ATTRIBUTES_OVER_MMAPPED_FILE, 
           nextMask(true,  // former USE_SMALL_ATTR_TABLE, feel free to re-use
           nextMask(PersistentHashMapValueStorage.COMPRESSION_ENABLED,
           nextMask(FileSystemUtil.DO_NOT_RESOLVE_SYMLINKS,
           nextMask(ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue(),
           nextMask(USE_FAST_NAMES_IMPLEMENTATION,
           nextMask(USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION, 0)))))))))))));
    //@formatter:on
  }

  /**
   * Factory
   *
   * @param storagesDirectoryPath directory there to put all FS-records files ('caches' directory)
   */
  public static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath) throws UncheckedIOException {
    return connect(storagesDirectoryPath, getDefaultErrorHandler());
  }

  public static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath,
                                      @NotNull ErrorHandler errorHandler) throws UncheckedIOException {
    if (IOUtil.isSharedCachesEnabled()) {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
    }
    try {
      int currentVersion = currentImplementationVersion();
      VFSInitializationResult initializationResult = PersistentFSConnector.connect(
        storagesDirectoryPath,
        currentVersion
      );

      PersistentFSConnection connection = initializationResult.connection;

      Supplier<InvertedNameIndex> invertedNameIndexLazy = asyncFillInvertedNameIndex(connection.getRecords());

      LOG.info("VFS initialized: " + NANOSECONDS.toMillis(initializationResult.totalInitializationDurationNs) + " ms, " +
               initializationResult.attemptsFailures.size() + " failed attempts, " +
               initializationResult.connection.recoveryInfo().recoveredErrors.size() + " error(s) were recovered");

      PersistentFSContentAccessor contentAccessor = new PersistentFSContentAccessor(connection);
      PersistentFSAttributeAccessor attributeAccessor = new PersistentFSAttributeAccessor(connection);
      PersistentFSRecordAccessor recordAccessor = new PersistentFSRecordAccessor(contentAccessor, attributeAccessor, connection);
      PersistentFSTreeAccessor treeAccessor = attributeAccessor.supportsRawAccess() && USE_RAW_ACCESS_TO_READ_CHILDREN ?
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
                                          new MRUFileNameCache(connection.getNames()) :
                                          new SLRUFileNameCache(connection.getNames());
      //cache.close() mostly just stops regular telemetry
      closeables.add(cacheOverEnumerator);
      this.fileNamesEnumerator = cacheOverEnumerator;
    }
    else {
      this.fileNamesEnumerator = connection.getNames();
    }

    if (BACKGROUND_VFS_FLUSH) {
      final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
      flushingTask = PersistentFSConnection.startFlusher(scheduler, connection, USE_GENTLE_FLUSHER);
    }
    else {
      flushingTask = null;
    }
  }

  //========== lifecycle: ========================================

  @Override
  public synchronized void close() {
    if (!closed) {
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

      closed = true;


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
        PersistentFSConnector.disconnect(connection);
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
    return closed;
  }

  void checkNotClosed() {
    if (closed) {
      throw alreadyClosedException();
    }
  }

  private @NotNull RuntimeException alreadyClosedException() {
    AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS is already closed (disposed)");
    if (closedStackTrace != null) {
      alreadyDisposed.addSuppressed(closedStackTrace);
    }

    if (!WRAP_ADE_IN_PCE) {
      return alreadyDisposed;
    }

    return ContainerUtilKt.wrapAlreadyDisposedError(alreadyDisposed);
  }


  //========== general FS records properties: ========================================

  public int getVersion() {
    return currentVersion;
  }

  public long getCreationTimestamp() {
    checkNotClosed();
    try {
      return connection.getTimestamp();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public VFSInitializationResult initializationResult() {
    return initializationResult;
  }

  //========== modifications counters: ========================================

  long getInvertedNameIndexModCount() {
    return invertedNameIndexModCount.get();
  }

  @TestOnly
  int getPersistentModCount() {
    checkNotClosed();
    return connection.getPersistentModCount();
  }

  //========== FS records persistence: ========================================

  @TestOnly
  void force() {
    checkNotClosed();
    try {
      connection.doForce();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @TestOnly
  boolean isDirty() {
    checkNotClosed();
    return connection.isDirty();
  }

  //========== record allocations: ========================================

  int createRecord() {
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
    return connection.getFreeRecords();
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
      connection.markDirty();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private void markAsDeletedRecursively(int fileId) throws IOException {
    IntList ids = new IntArrayList();
    ids.add(fileId);
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.getInt(i);
      //FiXME RC: what if id is already deleted -> listIds(id) fails with 'attribute already deleted'?
      ids.addElements(ids.size(), listIds(id));
    }
    PersistentFSRecordsStorage records = connection.getRecords();
    InvertedNameIndex invertedNameIndex = invertedNameIndexLazy.get();
    // delete children first:
    for (int i = ids.size() - 1; i >= 0; i--) {
      int id = ids.getInt(i);
      int nameId = records.getNameId(id);
      int flags = records.getFlags(id);

      if (PersistentFS.isDirectory(flags)) {
        treeAccessor.deleteDirectoryRecord(id);
      }
      recordAccessor.markRecordAsDeleted(id);

      invertedNameIndex.updateFileName(id, NULL_NAME_ID, nameId);
    }
    invertedNameIndexModCount.incrementAndGet();
  }


  //========== FS roots manipulation: ========================================

  int @NotNull [] listRoots() {
    checkNotClosed();
    try {
      return treeAccessor.listRoots();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl) {
    checkNotClosed();
    try {
      return treeAccessor.findOrCreateRootRecord(rootUrl);
    }
    catch (Throwable t) {
      //not only IOException: almost everything thrown from .findOrCreateRootRecord() is a sign of VFS structure corruption
      throw handleError(t);
    }
  }

  void forEachRoot(@NotNull ObjIntConsumer<? super String> rootConsumer) {
    checkNotClosed();
    try {
      treeAccessor.forEachRoot((rootId, rootUrlId) -> {
        String rootUrl = getNameByNameId(rootUrlId);
        rootConsumer.accept(rootUrl, rootId);
      });
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
    try {
      treeAccessor.deleteRootRecord(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
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
      return treeAccessor.mayHaveChildren(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean wereChildrenAccessed(int fileId) {
    try {
      return treeAccessor.wereChildrenAccessed(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int @NotNull [] listIds(int fileId) {
    try {
      return treeAccessor.listIds(fileId);
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
      return treeAccessor.doLoadChildren(parentId);
    }
    catch (IOException | IllegalArgumentException e) {
      throw handleError(e);
    }
  }

  @NotNull
  @Unmodifiable
  List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, ChildInfo::getName);
  }

  /** Perform operation on children and save the list atomically */
  @NotNull
  ListResult update(@NotNull VirtualFile parent,
                    int parentId,
                    @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
    SlowOperations.assertSlowOperationsAreAllowed();
    PersistentFSConnection.ensureIdIsValid(parentId);

    updateLock.lock(parentId);
    try {
      ListResult children = list(parentId);
      ListResult modifiedChildren = childrenConvertor.apply(children);

      // optimization: when converter returned unchanged children (see e.g. PersistentFSImpl.findChildInfo())
      // then do not save them back again unnecessarily
      if (!modifiedChildren.equals(children)) {
        //if (LOG.isDebugEnabled()) {
        //  LOG.debug("Update children for " + parent + " (id = " + parentId + "); old = " + children + ", new = " + modifiedChildren);
        //}
        checkNotClosed();

        //TODO RC: why we update symlinks here, under the lock?
        updateSymlinksForNewChildren(parent, children, modifiedChildren);

        treeAccessor.doSaveChildren(parentId, modifiedChildren);
        connection.markRecordAsModified(parentId);
      }
      return modifiedChildren;
    }
    catch (ProcessCanceledException e) {
      // NewVirtualFileSystem.list methods can be interrupted now
      throw e;
    }
    catch (Throwable e) {
      throw handleError(e);
    }
    finally {
      updateLock.unlock(parentId);
    }
  }

  void moveChildren(int fromParentId,
                    int toParentId) {
    assert fromParentId > 0 : fromParentId;
    assert toParentId > 0 : toParentId;

    if (fromParentId == toParentId) return;

    int minId = Math.min(fromParentId, toParentId);
    int maxId = Math.max(fromParentId, toParentId);

    checkNotClosed();
    updateLock.lock(minId);
    try {
      updateLock.lock(maxId);
      try {
        try {
          ListResult childrenToMove = list(fromParentId);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Move children from " + fromParentId + " to " + toParentId + "; children = " + childrenToMove);
          }

          for (ChildInfo childToMove : childrenToMove.children) {
            setParent(childToMove.getId(), toParentId);
          }

          treeAccessor.doSaveChildren(toParentId, childrenToMove);
          connection.markRecordAsModified(toParentId);

          treeAccessor.doSaveChildren(fromParentId, new ListResult(getModCount(fromParentId), Collections.emptyList(), fromParentId));
          connection.markRecordAsModified(fromParentId);
        }
        catch (ProcessCanceledException e) {
          // NewVirtualFileSystem.list methods can be interrupted now
          throw e;
        }
        catch (Throwable e) {
          throw handleError(e);
        }
      }
      finally {
        updateLock.unlock(maxId);
      }
    }
    finally {
      updateLock.unlock(minId);
    }
  }

  //========== symlink manipulation: ========================================

  @VisibleForTesting
  void updateSymlinksForNewChildren(@NotNull VirtualFile parent,
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
    try {
      checkNotClosed();
      connection.markDirty();
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
    try {
      checkNotClosed();
      return connection.getNames().forEach((nameId, name) -> processor.process(name));
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean processFilesWithNames(@NotNull Set<String> names,
                                @NotNull IntPredicate processor) {
    try {
      checkNotClosed();

      if (names.isEmpty()) {
        return true;
      }

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
  int getFlags(int fileId) {
    try {
      checkNotClosed();
      return connection.getRecords().getFlags(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean isDeleted(int fileId) {
    try {
      return recordAccessor.isDeleted(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getModCount(int fileId) {
    try {
      checkNotClosed();
      return connection.getRecords().getModCount(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  public int getParent(int fileId) {
    try {
      checkNotClosed();
      int parentId = connection.getRecords().getParent(fileId);
      if (parentId == fileId) {
        throw new IllegalStateException("Cyclic parent child relations in the database: fileId = " + fileId + " == parentId");
      }

      return parentId;
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setParent(int fileId,
                 int parentId) {
    if (fileId == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    try {
      checkNotClosed();
      connection.getRecords().setParent(fileId, parentId);
      connection.markDirty();
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
    try {
      checkNotClosed();
      return fileNamesEnumerator.enumerate(name);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  /** @return name by fileId, using FileNameCache */
  public @NotNull String getName(int fileId) {
    try {
      checkNotClosed();
      int nameId = connection.getRecords().getNameId(fileId);
      return nameId == NULL_NAME_ID ? "" : fileNamesEnumerator.valueOf(nameId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public int getNameIdByFileId(int fileId) {
    try {
      checkNotClosed();
      return connection.getRecords().getNameId(fileId);
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
    try {
      checkNotClosed();
      int nameId = getNameId(name);

      int previousNameId = connection.getRecords().updateNameId(fileId, nameId);
      connection.markDirty();

      invertedNameIndexLazy.get().updateFileName(fileId, nameId, previousNameId);
      invertedNameIndexModCount.incrementAndGet();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setFlags(int fileId,
                @PersistentFS.Attributes int flags) {
    try {
      checkNotClosed();
      if (connection.getRecords().setFlags(fileId, flags)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  long getLength(int fileId) {
    try {
      checkNotClosed();
      return connection.getRecords().getLength(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setLength(int fileId,
                 long len) {
    try {
      checkNotClosed();
      if (connection.getRecords().setLength(fileId, len)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  long getTimestamp(int fileId) {
    try {
      checkNotClosed();
      return connection.getRecords().getTimestamp(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setTimestamp(int fileId,
                    long value) {
    try {
      checkNotClosed();
      if (connection.getRecords().setTimestamp(fileId, value)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getContentRecordId(int fileId) {
    try {
      return connection.getRecords().getContentRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  SimpleStringPersistentEnumerator getEnumeratedAttributes() {
    return connection.getEnumeratedAttributes();
  }

  int getAttributeRecordId(int fileId) {
    try {
      return connection.getRecords().getAttributeRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * @return nameId > 0
   */
  int updateRecordFields(int fileId,
                         int parentId,
                         @NotNull FileAttributes attributes,
                         @NotNull String name,
                         boolean cleanAttributeRef) {
    int nameId = getNameId(name);
    long timestamp = attributes.lastModified;
    long length = attributes.isDirectory() ? -1L : attributes.length;
    int flags = PersistentFSImpl.fileAttributesToFlags(attributes);

    try {
      fillRecord(fileId, timestamp, length, flags, nameId, parentId, cleanAttributeRef);
    }
    catch (IOException e) {
      throw handleError(e);
    }

    invertedNameIndexLazy.get().updateFileName(fileId, nameId, NULL_NAME_ID);
    invertedNameIndexModCount.incrementAndGet();

    return nameId;
  }

  void fillRecord(int fileId,
                  long timestamp,
                  long length,
                  int flags,
                  int nameId,
                  int parentId,
                  boolean overwriteMissed) throws IOException {
    checkNotClosed();
    connection.getRecords().fillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteMissed);
    connection.markDirty();
  }


  //========== file attributes accessors: ========================================

  @Nullable
  AttributeInputStream readAttributeWithLock(int fileId,
                                             @NotNull FileAttribute attribute) {
    //RC: attributeAccessor acquires lock anyway, no need for additional lock here
    try {
      return readAttribute(fileId, attribute);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /** must be called under r or w lock */
  private @Nullable AttributeInputStream readAttribute(int fileId,
                                                       @NotNull FileAttribute attribute) throws IOException {
    try {
      return attributeAccessor.readAttribute(fileId, attribute);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @NotNull
  AttributeOutputStream writeAttribute(int fileId,
                                       @NotNull FileAttribute attribute) {
    return attributeAccessor.writeAttribute(fileId, attribute);
  }

  //'raw' (lambda + ByteBuffer instead of Input/OutputStream) attributes access: experimental

  @ApiStatus.Internal
  boolean supportsRawAttributesAccess() {
    return attributeAccessor.supportsRawAccess();
  }

  @ApiStatus.Internal
  <R> @Nullable R readAttributeRaw(int fileId,
                                   @NotNull FileAttribute attribute,
                                   ByteBufferReader<R> reader) {
    //RC: attributeAccessor acquires lock anyway, no need for additional lock here
    try {
      return attributeAccessor.readAttributeRaw(fileId, attribute, reader);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @ApiStatus.Internal
  void writeAttributeRaw(int fileId,
                         @NotNull FileAttribute attribute,
                         @NotNull ByteBufferWriter writer) {
    attributeAccessor.writeAttributeRaw(fileId, attribute, writer);
  }


  //========== file content accessors: ========================================

  @Nullable
  InputStream readContent(int fileId) {
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
  DataOutputStream writeContent(int fileId,
                                boolean fixedSize) {
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

  void writeContent(int fileId,
                    @NotNull ByteArraySequence bytes,
                    boolean fixedSize) {
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
  int writeContentRecord(@NotNull ByteArraySequence content) {
    try {
      return contentAccessor.writeContentRecord(content);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  //========== aux: ========================================

  /**
   * This method creates 'VFS rebuild marker', which forces VFS to rebuild on the next startup.
   * If cause argument is not null -- this scenario is considered an 'error', and warnings are
   * logged, if cause is null -- the scenario is considered not an 'error', but a regular
   * request -- e.g. no errors logged.
   */
  public void scheduleRebuild(final @Nullable String diagnosticMessage,
                              final @Nullable Throwable cause) {
    checkNotClosed();
    connection.scheduleVFSRebuild(diagnosticMessage, cause);
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
    if (e instanceof ClosedStorageException || closed) {
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

  PersistentFSAttributeAccessor attributeAccessor() {
    return attributeAccessor;
  }

  PersistentFSTreeAccessor treeAccessor() {
    return treeAccessor;
  }

  PersistentFSRecordAccessor recordAccessor() {
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
