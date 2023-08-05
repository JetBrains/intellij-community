// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.actions.cache.RecoverVfsFromLogService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StringPersistentEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeSizeStreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage;
import com.intellij.openapi.vfs.newvfs.persistent.intercept.*;
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogEx;
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogImpl;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoverer;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoveryInfo;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.*;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import com.intellij.util.io.storage.*;
import com.intellij.util.io.storage.lf.RefCountingContentStorageImplLF;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class keeps state during initialization.
 * <p>
 * Right now the state is used for 2 things:
 * <ol>
 * <li>For cleanup: if an attempt to initialize VFS is failed, we need to carefully clean up all intermediate
 * states -- otherwise uncleaned remnants could fail the next attempt also.</li>
 * <li>For a recovery: if VFS opened/initialized with some problems -- this class keeps that partially-initialized
 * state for {@link VFSRecoverer}s to fix.</li>
 * </ol>
 */
@ApiStatus.Internal
public final class PersistentFSLoader {
  private static final Logger LOG = Logger.getInstance(PersistentFSLoader.class);

  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT = new StorageLockContext(false, true);

  private final PersistentFSPaths vfsPaths;
  private final ExecutorService executorService;

  public final boolean enableVfsLog;

  public final @NotNull Path namesFile;
  public final @NotNull Path attributesFile;
  public final @NotNull Path contentsFile;
  public final @NotNull Path contentsHashesFile;
  public final @NotNull Path recordsFile;
  public final @NotNull Path enumeratedAttributesFile;
  public final @NotNull Path vfsLogDir;

  public final @NotNull Path corruptionMarkerFile;
  public final @NotNull Path storagesReplacementMarkerFile;

  private @Nullable VfsLogEx vfsLog = null;
  private PersistentFSRecordsStorage recordsStorage = null;
  private ScannableDataEnumeratorEx<String> namesStorage = null;
  private AbstractAttributesStorage attributesStorage = null;
  private RefCountingContentStorage contentsStorage = null;
  private ContentHashEnumerator contentHashesEnumerator = null;
  private SimpleStringPersistentEnumerator attributesEnumerator = null;

  //lazy property reusableFileIds and its calculating future (for closing)
  private Future<IntList> collectDeletedFileRecordsTask;
  private NotNullLazyValue<IntList> reusableFileIdsLazy = null;


  /** List of errors we met during VFS loading, but were able to +/- recover from */
  private final List<VFSInitException> problemsDuringLoad = new ArrayList<>();
  private final List<VFSInitException> problemsRecovered = new ArrayList<>();

  /** Directories those children are messed up, hence need refresh from the actual FS */
  private final IntSet directoriesIdsToRefresh = new IntOpenHashSet();
  /** Files that were messed up completely and were removed */
  private final IntSet filesIdsToInvalidate = new IntOpenHashSet();

  /**
   * true if during the recovery content storage was re-created, and previous contentIds are now
   * invalid (i.e. LocalHistory needs to be cleared then)
   */
  private boolean invalidateContentIds;


  PersistentFSLoader(@NotNull PersistentFSPaths persistentFSPaths,
                     boolean enableVfsLog,
                     ExecutorService pool) {
    recordsFile = persistentFSPaths.storagePath("records");
    namesFile = persistentFSPaths.storagePath("names");
    attributesFile = persistentFSPaths.storagePath("attributes");
    contentsFile = persistentFSPaths.storagePath("content");
    contentsHashesFile = persistentFSPaths.storagePath("contentHashes");
    enumeratedAttributesFile = persistentFSPaths.storagePath("attributes_enums");
    vfsLogDir = persistentFSPaths.getVfsLogStorage();

    corruptionMarkerFile = persistentFSPaths.getCorruptionMarkerFile();
    storagesReplacementMarkerFile = persistentFSPaths.getStoragesReplacementMarkerFile();

    vfsPaths = persistentFSPaths;
    this.executorService = pool;

    this.enableVfsLog = enableVfsLog;
  }

  public boolean replaceStoragesIfMarkerPresent() throws IOException {
    var applied = VfsRecoveryUtils.INSTANCE.applyStoragesReplacementIfMarkerExists(storagesReplacementMarkerFile);
    if (applied) LOG.info("PersistentFS storages replacement was applied");
    return applied;
  }

  public void failIfCorruptionMarkerPresent() throws IOException {
    if (Files.exists(corruptionMarkerFile)) {
      final List<String> corruptionCause = Files.readAllLines(corruptionMarkerFile, UTF_8);
      throw new VFSInitException(SCHEDULED_REBUILD, "Corruption marker file found\n\tcontent: " + corruptionCause);
    }
  }

  public void initializeStorages() throws IOException {
    if (enableVfsLog) {
      vfsLog = new VfsLogImpl(vfsLogDir, false);
    }
    else {
      vfsLog = null;
    }

    Future<ScannableDataEnumeratorEx<String>> namesStorageFuture = executorService.submit(
      () -> createFileNamesEnumerator(namesFile)
    );
    Future<AbstractAttributesStorage> attributesStorageFuture = executorService.submit(
      () -> createAttributesStorage(attributesFile)
    );
    Future<RefCountingContentStorage> contentsStorageFuture = executorService.submit(
      () -> createContentStorage(contentsFile)
    );
    Future<PersistentFSRecordsStorage> recordsStorageFuture = executorService.submit(
      () -> createRecordsStorage(recordsFile)
    );

    //Initiate async scanning of the recordsStorage to fill both invertedNameIndex and reusableFileIds,
    //  and create lazy-accessors for both.
    collectDeletedFileRecordsTask = executorService.submit(() -> {
      IntList reusableFileIds = new IntArrayList(1024);
      //fill up reusable (=deleted) records:
      PersistentFSRecordsStorage storage = recordsStorageFuture.get();
      storage.processAllRecords((fileId, nameId, flags, parentId, attributeRecordId, contentId, corrupted) -> {
        if (hasDeletedFlag(flags)) {
          reusableFileIds.add(fileId);
        }
      });
      LOG.info("VFS scanned: " + reusableFileIds.size() + " deleted files to reuse");
      return reusableFileIds;
    });
    //RC: we don't need volatile/atomicLazy, since computation is idempotent: same instance returned always.
    //    So _there could be_ a data race, but it is a benign race.
    reusableFileIdsLazy = NotNullLazyValue.lazy(() -> {
      try {
        return collectDeletedFileRecordsTask.get();
      }
      catch (Throwable e) {
        throw new IllegalStateException("Lazy reusableFileIds computation is failed", e);
      }
    });


    Future<ContentHashEnumerator> contentHashesEnumeratorFuture = executorService.submit(
      () -> createContentHashStorage(contentsHashesFile)
    );

    ExceptionUtil.runAllAndRethrowAllExceptions(
      new IOException(),
      () -> {
        //MAYBE RC: I'd like to have completely new Enumerator here:
        //          1. Without legacy issues with null vs 'null' strings
        //          2. With explicit 'id' stored in a file (instead of implicit id=row num)
        //          3. With CopyOnWrite concurrent strategy (hence very cheap .enumerate() for already enumerated values)
        //          ...next time we bump FSRecords version
        attributesEnumerator = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);
      },
      () -> {
        recordsStorage = recordsStorageFuture.get();
      },
      () -> {
        namesStorage = namesStorageFuture.get();
      },
      () -> {
        attributesStorage = attributesStorageFuture.get();
      },
      () -> {
        contentsStorage = contentsStorageFuture.get();
      },
      () -> {
        contentHashesEnumerator = contentHashesEnumeratorFuture.get();
      }
    );
  }

  public boolean recoverCachesFromVfsLogIfAppWasNotClosedProperly() throws IOException {
    VfsLogImpl vfsLog = new VfsLogImpl(vfsLogDir, true);
    try {
      if (!vfsLog.getWasProperlyClosedLastSession()) {
        LOG.warn("VFS was not properly closed last session. Recovering from VfsLog...");
        return recoverCachesFromVfsLog(vfsLog);
      }
    }
    finally {
      vfsLog.dispose();
    }
    return false;
  }

  private boolean recoverCachesFromVfsLog(@NotNull VfsLogImpl vfsLog) throws IOException {
    AtomicBoolean recoveredCaches = new AtomicBoolean(false);
    try (var queryContext = vfsLog.query()) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        recoveredCaches.set(
          RecoverVfsFromLogService.Companion.recoverSynchronouslyFromLastRecoveryPoint(queryContext, true)
        );
      });
    }
    catch (Throwable e) {
      LOG.error("VFS Caches recovery attempt has failed", e);
    }
    if (!recoveredCaches.get()) return false;
    if (!replaceStoragesIfMarkerPresent()) {
      throw new AssertionError("storages replacement didn't happen right after recovery");
    }
    return true;
  }

  public void ensureStoragesVersionsAreConsistent(int currentImplVersion) throws IOException {
    LOG.info("VFS: impl (expected) version=" + currentImplVersion +
             ", " + recordsStorage.recordsCount() + " file records" +
             ", " + contentsStorage.getRecordsCount() + " content blobs");


    if (currentImplVersion == 0) {
      throw new IllegalArgumentException("currentImplVersion(=" + currentImplVersion + ") must be != 0");
    }
    //Versions of different storages should be either all set, and all == currentImplementationVersion,
    // or none set at all (& storages are all fresh & empty) -- everything but that is a version mismatch
    // and a trigger for VFS rebuild:

    int commonVersion = commonVersionIfExists(recordsStorage, attributesStorage, contentsStorage);

    if (commonVersion != currentImplVersion) {
      //If storages are just created -> commonVersion=0, and storages are empty.
      // => we should stamp them with current implVersion and go ahead.
      //Otherwise it is version mismatch and we should rebuild VFS storages from 0.
      boolean storagesAreEmpty = (recordsStorage.recordsCount() == 0);
      if (commonVersion == 0 && storagesAreEmpty) {//MAYBE RC: better check also attributes/contentsStorage.isEmpty()?
        //all storages are fresh new => assign their versions to the current one:
        setCurrentVersion(recordsStorage, attributesStorage, contentsStorage, currentImplVersion);
        return;
      }

      //if commonVersion > 0 => current VFS data has a consistent version, but != implVersion
      //                     => IMPL_VERSION_MISMATCH
      //if commonVersion = -1 => different VFS storages have inconsistent versions
      //                      => UNRECOGNIZED (I guess it is a rare case, most probably happens for users playing
      //                         hard with their IDE installations, most likely they are our QAs -- so the case
      //                         doesn't worth dedicated enum constant)
      VFSInitException.ErrorCategory rebuildCause = commonVersion > 0 ? IMPL_VERSION_MISMATCH : UNRECOGNIZED;
      throw new VFSInitException(
        rebuildCause,
        "FS repository detected version(=" + commonVersion + ") != current version(=" + currentImplVersion + ") -> VFS needs rebuild"
      );
    }
  }

  public void closeEverything() throws IOException {
    // Must wait for scanRecords task to finish, since the task uses mapped file, and we can't remove
    //  the mapped file (on Win) while there are usages.
    try {
      collectDeletedFileRecordsTask.get();
    }
    catch (Throwable t) {
      LOG.trace(t);
    }

    PersistentFSConnection.closeStorages(recordsStorage, namesStorage, attributesStorage, contentHashesEnumerator, contentsStorage, vfsLog);
  }

  public void deleteEverything() throws IOException {
    boolean deleted = FileUtil.delete(corruptionMarkerFile.toFile());
    if (!deleted) {
      LOG.info("Can't delete " + corruptionMarkerFile);
    }

    deleted = IOUtil.deleteAllFilesStartingWith(namesFile);
    if (!deleted) {
      LOG.info("Can't delete " + namesFile);
    }

    if (FSRecordsImpl.USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION) {
      deleted = AttributesStorageOverBlobStorage.deleteStorageFiles(attributesFile);
    }
    else {
      deleted = AbstractStorage.deleteFiles(attributesFile);
    }
    if (!deleted) {
      LOG.info("Can't delete " + attributesFile);
    }

    deleted = AbstractStorage.deleteFiles(contentsFile);
    if (!deleted) {
      LOG.info("Can't delete " + contentsFile);
    }

    deleted = IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
    if (!deleted) {
      LOG.info("Can't delete " + contentsHashesFile);
    }

    if (recordsStorage != null) {
      try {
        recordsStorage.closeAndRemoveAllFiles();
      }
      catch (IOException ex) {
        LOG.info("Can't delete fs-records: " + ex.getMessage(), ex);
      }
    }
    else {
      deleted = IOUtil.deleteAllFilesStartingWith(recordsFile);
      if (!deleted) {
        LOG.info("Can't delete " + recordsFile);
      }
    }

    deleted = IOUtil.deleteAllFilesStartingWith(vfsPaths.getRootsBaseFile());
    if (!deleted) {
      LOG.info("Can't delete " + vfsPaths.getRootsBaseFile());
    }

    deleted = IOUtil.deleteAllFilesStartingWith(enumeratedAttributesFile);
    if (!deleted) {
      LOG.info("Can't delete " + enumeratedAttributesFile);
    }

    if (vfsLog != null) {
      try {
        VfsLogImpl.clearStorage(vfsLogDir);
      }
      catch (IOException e) {
        LOG.info("Can't clear " + vfsLogDir, e);
      }
    }
  }

  public PersistentFSConnection createConnection(@NotNull List<ConnectionInterceptor> interceptors) throws IOException {
    return new PersistentFSConnection(
      vfsPaths,
      recordsStorage,
      namesStorage,
      attributesStorage,
      contentsStorage,
      contentHashesEnumerator,
      attributesEnumerator,
      vfsLog,
      reusableFileIdsLazy,
      new VFSRecoveryInfo(
        problemsRecovered,
        invalidateContentIds,
        directoriesIdsToRefresh,
        filesIdsToInvalidate
      ),
      interceptors
    );
  }

  public void selfCheck() throws IOException {
    //VFS errors early on startup cause terrifying UX error messages. It is much better to catch VFS
    // corruption early on and rebuild VFS from 0.
    //But we also don't want to always check each VFS file on startup -- it delays regular startup
    // (i.e. without corruptions) quite a lot.
    //So the tradeoff: we use few heuristics to quickly check for the most likely signs of corruption,
    // and if we find any such sign -- switch to more vigilant checking:

    if (recordsStorage.getConnectionStatus() != PersistentFSHeaders.SAFELY_CLOSED_MAGIC) {
      addProblem(NOT_CLOSED_PROPERLY, "VFS wasn't safely shut down: records.connectionStatus != SAFELY_CLOSED");
    }
    int errorsAccumulated = recordsStorage.getErrorsAccumulated();
    if (errorsAccumulated > 0) {
      addProblem(HAS_ERRORS_IN_PREVIOUS_SESSION, "VFS accumulated " + errorsAccumulated + " errors in last session");
    }

    int largestId = contentHashesEnumerator.getLargestId();
    int liveRecordsCount = contentsStorage.getRecordsCount();
    if (largestId != liveRecordsCount) {
      addProblem(CONTENT_STORAGES_NOT_MATCH,
                 "Content storage is not match content hash enumerator: " +
                 "contents.records(=" + liveRecordsCount + ") != contentHashes.largestId(=" + largestId + ")"
      );
    }

    if (attributesEnumerator.isEmpty() && !attributesStorage.isEmpty()) {
      addProblem(UNRECOGNIZED, "Attributes enumerator is empty, while attributesStorage is !empty");
    }

    int maxAllocatedID = recordsStorage.maxAllocatedID();

    //Faster to look only for the first error of each kind:
    boolean nameStorageHasErrors = false;
    boolean contentHashesStorageHasErrors = false;

    //Try to resolve few nameId/contentId against apt enumerators -- which is quite likely fails
    //if enumerators' files are corrupted anyhow, hence serves as a self-check heuristic.
    //Check every file is quite slow, but it is definitely worth checking _some_.
    // A tradeoff: if there were no signs of corruption -- check only fileId=2, 4, 8...
    // -- always < 32 checks, given fileId is int32.
    if (problemsDuringLoad.isEmpty()) {
      for (int fileId = FSRecords.MIN_REGULAR_FILE_ID; fileId <= maxAllocatedID; fileId *= 2) {
        if (!nameStorageHasErrors) {
          if (!nameResolvedSuccessfully(fileId)) {
            nameStorageHasErrors = true;
          }
        }

        if (!contentHashesStorageHasErrors) {
          if (!contentResolvedSuccessfully(fileId)) {
            contentHashesStorageHasErrors = true;
          }
        }
      }
    }

    //...If there are some errors, or other signs of possible corruption
    // -> switch to full scan
    if (!problemsDuringLoad.isEmpty()) {
      for (int fileId = FSRecords.MIN_REGULAR_FILE_ID; fileId <= maxAllocatedID; fileId++) {
        if (!nameStorageHasErrors) {
          if (!nameResolvedSuccessfully(fileId)) {
            nameStorageHasErrors = true;
          }
        }

        if (!contentHashesStorageHasErrors) {
          if (!contentResolvedSuccessfully(fileId)) {
            contentHashesStorageHasErrors = true;
          }
        }
      }
    }
  }

  private boolean contentResolvedSuccessfully(int fileId) throws IOException {
    int contentId = recordsStorage.getContentRecordId(fileId);
    if (contentHashesEnumerator != null
        && contentId != DataEnumeratorEx.NULL_ID) {
      try {
        byte[] contentHash = contentHashesEnumerator.valueOf(contentId);
        if (contentHash == null) {
          addProblem(CONTENT_STORAGES_INCOMPLETE,
                     "file[#" + fileId + "].contentId(=" + contentId + ") is not present in contentHashesEnumerator"
          );
          return false;
        }
        int reCheckContentId = contentHashesEnumerator.tryEnumerate(contentHash);
        if (reCheckContentId != contentId) {
          addProblem(CONTENT_STORAGES_INCOMPLETE,
                     "contentHashesEnumerator is corrupted: file[#" + fileId + "]" +
                     ".contentId(=" + contentId + ") -> [" + Arrays.toString(contentHash) + "] -> tryEnumerate() -> " + reCheckContentId
          );
          return false;
        }
      }
      catch (Throwable t) {
        addProblem(CONTENT_STORAGES_INCOMPLETE,
                   "file[#" + fileId + "].contentId(=" + contentId + ") failed resolution in contentHashesEnumerator", t
        );
        return false;
      }
    }

    return true;
  }

  private boolean nameResolvedSuccessfully(int fileId) throws IOException {
    int nameId = recordsStorage.getNameId(fileId);
    if (nameId == DataEnumeratorEx.NULL_ID) {
      return false;
    }
    try {
      String name = namesStorage.valueOf(nameId);
      if (name == null) {
        addProblem(NAME_STORAGE_INCOMPLETE,
                   "file[#" + fileId + "].nameId(=" + nameId + ") is not present in namesEnumerator");
        return false;
      }
      else {
        int reCheckNameId = namesStorage.tryEnumerate(name);
        if (reCheckNameId != nameId) {
          addProblem(NAME_STORAGE_INCOMPLETE,
                     "namesEnumerator is corrupted: file[#" + fileId + "]" +
                     ".nameId(=" + nameId + ") -> [" + name + "] -> tryEnumerate() -> " + reCheckNameId
          );
          return false;
        }
      }
    }
    catch (Throwable t) {
      addProblem(NAME_STORAGE_INCOMPLETE,
                 "file[#" + fileId + "].nameId(=" + nameId + ") failed resolution in namesEnumerator", t
      );
      return false;
    }
    return true;
  }

  private void addProblem(@NotNull VFSInitException.ErrorCategory type,
                          @NotNull String message) {
    addProblem(type, message, null);
  }

  private void addProblem(@NotNull VFSInitException.ErrorCategory type,
                          @NotNull String message,
                          @Nullable Throwable cause) {
    LOG.warn("[VFS load problem]: " + message, cause);
    if (cause == null) {
      this.problemsDuringLoad.add(new VFSInitException(type, message));
    }
    else {
      this.problemsDuringLoad.add(new VFSInitException(type, message, cause));
    }
  }

  public boolean isJustCreated() throws IOException {
    return recordsStorage.recordsCount() == 0
           && attributesStorage.isEmpty()
           && contentsStorage().getRecordsCount() == 0;
  }


  public @NotNull AbstractAttributesStorage createAttributesStorage(@NotNull Path attributesFile) throws IOException {
    AbstractAttributesStorage storage = createAttributesStorage_makeStorage(attributesFile);
    if (vfsLog != null) {
      var attributesInterceptors = vfsLog.getConnectionInterceptors().stream()
        .filter(AttributesInterceptor.class::isInstance)
        .map(AttributesInterceptor.class::cast)
        .toList();
      storage = InterceptorInjection.INSTANCE.injectInAttributes(storage, attributesInterceptors);
    }
    return storage;
  }

  private static @NotNull AbstractAttributesStorage createAttributesStorage_makeStorage(@NotNull Path attributesFile) throws IOException {
    if (FSRecordsImpl.USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION) {
      LOG.info("VFS uses new (streamlined) attributes storage");
      //avg record size is ~60b, hence I've chosen minCapacity=64 bytes, and defaultCapacity= 2*minCapacity
      final SpaceAllocationStrategy allocationStrategy = new SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy(128, 64, 30);
      final StreamlinedBlobStorage blobStorage;
      if (PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
        blobStorage = new StreamlinedBlobStorageOverLockFreePagesStorage(
          PagedFileStorageWithRWLockedPageContent.createWithDefaults(
            attributesFile,
            PERSISTENT_FS_STORAGE_CONTEXT,
            PageCacheUtils.DEFAULT_PAGE_SIZE,
            /*nativeByteOrder: */  true,
            PageContentLockingStrategy.LOCK_PER_PAGE
          ),
          allocationStrategy
        );
      }
      else {
        blobStorage = new LargeSizeStreamlinedBlobStorage(
          new PagedFileStorage(
            attributesFile,
            PERSISTENT_FS_STORAGE_CONTEXT,
            PageCacheUtils.DEFAULT_PAGE_SIZE,
            /*valuesAreAligned: */ true,
            /*nativeByteOrder: */  true
          ),
          allocationStrategy
        );
      }
      return new AttributesStorageOverBlobStorage(blobStorage);
    }
    else {
      LOG.info("VFS uses regular attributes storage");
      boolean bulkAttrReadSupport = false;
      boolean inlineAttributes = true;
      return new AttributesStorageOld(
        bulkAttrReadSupport,
        inlineAttributes,
        new Storage(attributesFile, PersistentFSConnection.REASONABLY_SMALL) {
          @Override
          protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext context,
                                                            @NotNull Path recordsFile) throws IOException {
            return new CompactRecordsTable(recordsFile, context, false);
          }
        });
    }
  }

  public static @NotNull ScannableDataEnumeratorEx<String> createFileNamesEnumerator(@NotNull Path namesFile) throws IOException {
    if (FSRecordsImpl.USE_FAST_NAMES_IMPLEMENTATION) {
      LOG.info("VFS uses fast names enumerator");
      return new StringPersistentEnumerator(namesFile);
    }
    else {
      LOG.info("VFS uses strict names enumerator");
      return new PersistentStringEnumerator(namesFile, PERSISTENT_FS_STORAGE_CONTEXT);
    }
  }

  public @NotNull RefCountingContentStorage createContentStorage(@NotNull Path contentsFile) throws IOException {
    RefCountingContentStorage storage = createContentStorage_makeStorage(contentsFile);
    if (vfsLog != null) {
      var contentInterceptors = vfsLog.getConnectionInterceptors().stream()
        .filter(ContentsInterceptor.class::isInstance)
        .map(ContentsInterceptor.class::cast)
        .toList();
      storage = InterceptorInjection.INSTANCE.injectInContents(storage, contentInterceptors);
    }
    return storage;
  }

  private static @NotNull RefCountingContentStorage createContentStorage_makeStorage(@NotNull Path contentsFile) throws IOException {
    // sources usually zipped with 4x ratio
    if (PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
      return new RefCountingContentStorageImplLF(
        contentsFile,
        CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
        /*useContentHashes: */ true
      );
    }
    else {
      return new RefCountingContentStorageImpl(
        contentsFile,
        CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
        /*useContentHashes: */ true
      );
    }
  }

  public static @NotNull ContentHashEnumerator createContentHashStorage(@NotNull Path contentsHashesFile) throws IOException {
    return new ContentHashEnumerator(contentsHashesFile, PERSISTENT_FS_STORAGE_CONTEXT);
  }

  public @NotNull PersistentFSRecordsStorage createRecordsStorage(@NotNull Path recordsFile) throws IOException {
    PersistentFSRecordsStorage storage = PersistentFSRecordsStorageFactory.createStorage(recordsFile);
    if (vfsLog != null) {
      var recordsInterceptors = vfsLog.getConnectionInterceptors().stream()
        .filter(RecordsInterceptor.class::isInstance)
        .map(RecordsInterceptor.class::cast)
        .toList();
      storage = InterceptorInjection.INSTANCE.injectInRecords(storage, recordsInterceptors);
    }
    return storage;
  }

  /** @return common version of all 3 storages, or -1, if their versions are differ (i.e. inconsistent) */
  private static int commonVersionIfExists(@NotNull PersistentFSRecordsStorage records,
                                           @NotNull AbstractAttributesStorage attributes,
                                           @NotNull RefCountingContentStorage contents) throws IOException {
    final int recordsVersion = records.getVersion();
    final int attributesVersion = attributes.getVersion();
    final int contentsVersion = contents.getVersion();
    if (attributesVersion != recordsVersion /*|| contentsVersion != recordsVersion*/) {
      LOG.info("VFS storages are of different versions: " +
               "records(=" + recordsVersion + "), " +
               "attributes(=" + attributesVersion + "), " +
               "content(=" + contentsVersion + ")");
      return -1;
    }

    return recordsVersion;
  }

  private static void setCurrentVersion(@NotNull PersistentFSRecordsStorage records,
                                        @NotNull AbstractAttributesStorage attributes,
                                        @NotNull RefCountingContentStorage contents,
                                        int version) throws IOException {
    records.setVersion(version);
    attributes.setVersion(version);
    contents.setVersion(version);
    records.setConnectionStatus(PersistentFSHeaders.SAFELY_CLOSED_MAGIC);
  }


  //======================== accessors: ============================================================================

  public PersistentFSRecordsStorage recordsStorage() {
    return recordsStorage;
  }

  public ScannableDataEnumeratorEx<String> namesStorage() {
    return namesStorage;
  }

  public AbstractAttributesStorage attributesStorage() {
    return attributesStorage;
  }

  public RefCountingContentStorage contentsStorage() {
    return contentsStorage;
  }

  public ContentHashEnumerator contentHashesEnumerator() {
    return contentHashesEnumerator;
  }

  public VfsLogEx vfsLog() {
    return vfsLog;
  }

  public SimpleStringPersistentEnumerator attributesEnumerator() {
    return attributesEnumerator;
  }

  NotNullLazyValue<IntList> reusableFileIdsLazy() {
    return reusableFileIdsLazy;
  }


  public void setNamesStorage(ScannableDataEnumeratorEx<String> namesStorage) {
    this.namesStorage = namesStorage;
  }

  public void setAttributesStorage(AbstractAttributesStorage attributesStorage) {
    this.attributesStorage = attributesStorage;
  }

  public void setContentsStorage(RefCountingContentStorage contentsStorage) {
    this.contentsStorage = contentsStorage;
  }

  public void setContentHashesEnumerator(ContentHashEnumerator contentHashesEnumerator) {
    this.contentHashesEnumerator = contentHashesEnumerator;
  }

  public void setAttributesEnumerator(SimpleStringPersistentEnumerator attributesEnumerator) {
    this.attributesEnumerator = attributesEnumerator;
  }


  // ============================== for VFSRecoverer to use: ============================================================

  public List<VFSInitException> problemsDuringLoad() {
    return Collections.unmodifiableList(problemsDuringLoad);
  }

  public List<VFSInitException> problemsDuringLoad(@NotNull VFSInitException.ErrorCategory firstCategory,
                                                   @NotNull VFSInitException.ErrorCategory... restCategories) {
    EnumSet<VFSInitException.ErrorCategory> categories = EnumSet.of(firstCategory, restCategories);
    return problemsDuringLoad.stream()
      .filter(p -> categories.contains(p.category()))
      .toList();
  }

  public void problemsWereRecovered(@NotNull List<VFSInitException> recovered) {
    problemsDuringLoad.removeAll(recovered);
    problemsRecovered.addAll(recovered);
  }

  public void problemsRecoveryFailed(@NotNull List<VFSInitException> triedToRecover,
                                     @NotNull VFSInitException.ErrorCategory category,
                                     @NotNull String message) {
    problemsRecoveryFailed(triedToRecover, category, message, /*cause: */ null);
  }

  public void problemsRecoveryFailed(@NotNull List<VFSInitException> triedToRecover,
                                     @NotNull VFSInitException.ErrorCategory category,
                                     @NotNull String message,
                                     @Nullable Throwable cause) {
    problemsDuringLoad.removeAll(triedToRecover);
    VFSInitException recoveryFailed = (cause == null) ?
                                      new VFSInitException(category, message) :
                                      new VFSInitException(category, message, cause);
    triedToRecover.forEach(recoveryFailed::addSuppressed);
    problemsDuringLoad.add(recoveryFailed);
  }


  public void contentIdsInvalidated(boolean invalidated) {
    this.invalidateContentIds = invalidated;
  }

  public void postponeDirectoryRefresh(int directoryIdToRefresh) {
    this.directoriesIdsToRefresh.add(directoryIdToRefresh);
  }

  public void postponeFileInvalidation(int fileId) {
    this.filesIdsToInvalidate.add(fileId);
  }
}
