// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagedStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverPagedStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.content.CompressingAlgo;
import com.intellij.openapi.vfs.newvfs.persistent.dev.content.ContentHashEnumeratorOverDurableEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.content.ContentStorageAdapter;
import com.intellij.openapi.vfs.newvfs.persistent.dev.content.VFSContentStorageOverMMappedFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableStringEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoverer;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoveryInfo;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.*;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.*;
import static com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory.IfNotPageAligned.EXPAND_FILE;
import static com.intellij.util.io.storage.CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

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

  /**
   * We want the 'main exception' to be 1) IOException 2) with +/- descriptive message.
   * So:
   * => We look for such an exception among .exceptions, and if there is one -> use it as the main one
   * => Otherwise we create IOException with the first non-empty error message among .exceptions
   * In both cases, we attach all other exceptions to .suppressed list of the main exception
   */
  private static final @NotNull Function<List<? extends Throwable>, IOException> ASYNC_EXCEPTIONS_REPORTER = exceptions -> {
    IOException mainIoException = (IOException)exceptions.stream()
      .map(ex -> {
        //unwrap CompletionException from async processing
        return ex instanceof CompletionException ? ex.getCause() : ex;
      })
      .filter(e -> e instanceof IOException)
      .findFirst().orElse(null);

    if (mainIoException != null && !mainIoException.getMessage().isEmpty()) {
      for (Throwable exception : exceptions) {
        if (exception != mainIoException) {
          mainIoException.addSuppressed(exception);
        }
      }
    }
    else {
      String nonEmptyErrorMessage = exceptions.stream()
        .map(e -> ExceptionUtil.getNonEmptyMessage(e, ""))
        .filter(message -> !message.isBlank())
        .findFirst().orElse("<Error message not found>");
      mainIoException = new IOException(nonEmptyErrorMessage);
      for (Throwable exception : exceptions) {
        mainIoException.addSuppressed(exception);
      }
    }
    return mainIoException;
  };


  private final PersistentFSPaths vfsPaths;

  private final VFSAsyncTaskExecutor executorService;

  public final @NotNull Path namesFile;
  public final @NotNull Path attributesFile;
  public final @NotNull Path contentsFile;
  public final @NotNull Path contentsHashesFile;
  public final @NotNull Path recordsFile;
  public final @NotNull Path enumeratedAttributesFile;

  public final @NotNull Path corruptionMarkerFile;

  private PersistentFSRecordsStorage recordsStorage = null;
  private ScannableDataEnumeratorEx<String> namesStorage = null;
  private VFSAttributesStorage attributesStorage = null;
  private VFSContentStorage contentsStorage = null;
  private SimpleStringPersistentEnumerator attributesEnumerator = null;

  //lazy property reusableFileIds and its calculating future (for closing)
  private CompletableFuture<IntList> collectDeletedFileRecordsTask;
  private NotNullLazyValue<IntList> reusableFileIdsLazy = null;


  /** List of errors we met during VFS loading, but were able to +/- recover from */
  private final List<VFSInitException> problemsDuringLoad = new ArrayList<>();
  private final List<VFSInitException> problemsRecovered = new ArrayList<>();

  /** Directories those children are messed up, hence need refresh from the actual FS */
  private final IntSet directoriesIdsToRefresh = new IntOpenHashSet();
  /** Files that were messed up completely and were removed */
  private final IntSet filesIdsToInvalidate = new IntOpenHashSet();

  /**
   * true if during the recovery, content storage was re-created, and previous contentIds are now
   * invalid (i.e., LocalHistory needs to be cleared then)
   */
  private boolean invalidateContentIds;

  PersistentFSLoader(@NotNull PersistentFSPaths persistentFSPaths,
                     @NotNull VFSAsyncTaskExecutor pool) {
    recordsFile = persistentFSPaths.storagePath("records");
    namesFile = persistentFSPaths.storagePath("names");
    attributesFile = persistentFSPaths.storagePath("attributes");
    contentsFile = persistentFSPaths.storagePath("content");
    contentsHashesFile = persistentFSPaths.storagePath("contentHashes");
    enumeratedAttributesFile = persistentFSPaths.storagePath("attributes_enums");

    corruptionMarkerFile = persistentFSPaths.getCorruptionMarkerFile();

    vfsPaths = persistentFSPaths;
    this.executorService = pool;
  }

  public void failIfCorruptionMarkerPresent() throws IOException {
    if (Files.exists(corruptionMarkerFile)) {
      final List<String> corruptionCause = Files.readAllLines(corruptionMarkerFile, UTF_8);
      throw new VFSInitException(SCHEDULED_REBUILD, "Corruption marker file found\n\tcontent: " + corruptionCause);
    }
  }

  public void initializeStorages() throws IOException {
    CompletableFuture<ScannableDataEnumeratorEx<String>> namesStorageFuture =
      executorService.async(() -> createFileNamesEnumerator(namesFile));
    CompletableFuture<VFSAttributesStorage> attributesStorageFuture =
      executorService.async(() -> createAttributesStorage(attributesFile));
    CompletableFuture<VFSContentStorage> contentsStorageFuture =
      executorService.async(() -> createContentStorage(contentsHashesFile, contentsFile));
    CompletableFuture<PersistentFSRecordsStorage> recordsStorageFuture = executorService.async(() -> createRecordsStorage(recordsFile));

    //TODO RC: if !REUSE_DELETED_FILE_IDS -> check recordsStorage.maxAllocatedID() -> rebuild VFS if maxID >~ MAX_INT/2
    if (FSRecordsImpl.REUSE_DELETED_FILE_IDS) {
      //Initiate async scanning of the recordsStorage to fill reusableFileIds, and create lazy-accessor for it.
      collectDeletedFileRecordsTask = executorService.async(() -> {
        //TODO RC: limit max number of reusable fileIds -- i.e. what if there are millions of them?
        //         5-10_000 max should be enough.
        IntList reusableFileIds = new IntArrayList(1024);
        //fill up reusable (=deleted) records:
        PersistentFSRecordsStorage storage = recordsStorageFuture.join();
        storage.processAllRecords((fileId, nameId, flags, parentId, attributeRecordId, contentId, corrupted) -> {
          if (hasDeletedFlag(flags)) {
            reusableFileIds.add(fileId);
          }
        });
        LOG.info("VFS scanned: " + reusableFileIds.size() + " deleted files to reuse");
        return reusableFileIds;
      });
    }
    else {
      collectDeletedFileRecordsTask = CompletableFuture.completedFuture(new IntArrayList(0));
    }
    //RC: we don't need volatile/atomicLazy, since computation is idempotent: same instance returned always.
    //    So _there could be_ a data race, but it is a benign race.
    reusableFileIdsLazy = NotNullLazyValue.lazy(() -> {
      try {
        return collectDeletedFileRecordsTask.join();
      }
      catch (Throwable e) {
        throw new IllegalStateException("Lazy reusableFileIds computation is failed", e);
      }
    });

    ExceptionUtil.runAllAndRethrowAllExceptions(
      ASYNC_EXCEPTIONS_REPORTER,
      () -> {
        //MAYBE RC: I'd like to have completely new Enumerator here:
        //          1. Without legacy issues with null vs 'null' strings
        //          2. With explicit 'id' stored in a file (instead of implicit id=row num)
        attributesEnumerator = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);
      },
      () -> {
        recordsStorage = recordsStorageFuture.join();
      },
      () -> {
        namesStorage = namesStorageFuture.join();
      },
      () -> {
        attributesStorage = attributesStorageFuture.join();
      },
      () -> {
        contentsStorage = contentsStorageFuture.join();
      }
    );
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
      //If (commonVersion=0) AND (all storages are empty)
      //   => storages were just created
      //   => we should stamp them with current implVersion and go ahead.
      boolean storagesAreEmpty = recordsStorage.recordsCount() == 0
                                 && contentsStorage.isEmpty()
                                 && attributesStorage.isEmpty();
      if (commonVersion == 0 && storagesAreEmpty) {
        //all storages are fresh new => assign their versions to the current one:
        setCurrentVersion(recordsStorage, attributesStorage, contentsStorage, currentImplVersion);
        return;
      }

      //Otherwise: (not all storages are empty OR commonVersion != 0)
      //   => it is a version mismatch
      //   => we should rebuild VFS storages from 0.


      //if commonVersion > 0  => current VFS data _has_ a consistent version, but != implVersion
      //                      => IMPL_VERSION_MISMATCH
      //if commonVersion = -1 => different VFS storages have different, inconsistent versions
      //                      => UNRECOGNIZED (I guess it is a rare case, most probably happens if users play hard
      //                         with their IDE installations -- most likely they are our QAs -- or it is Nightly,
      //                         and we're changing VFS file layout... -- anyway, those are cases that are not worth
      //                         dedicated statistics/enum constant)
      VFSInitException.ErrorCategory rebuildCause = commonVersion > 0 ? IMPL_VERSION_MISMATCH : UNRECOGNIZED;
      throw new VFSInitException(
        rebuildCause,
        "VFS storages detected version(=" + commonVersion + ") != current impl version(=" + currentImplVersion + ") -> VFS needs rebuild"
      );
    }
  }

  public void closeEverything() throws IOException {
    // Must wait for scanRecords task to finish, since the task uses mapped file, and we can't remove
    //  the mapped file (on Win) while there are usages.
    try {
      collectDeletedFileRecordsTask.join();
    }
    catch (Throwable t) {
      LOG.trace(t);
    }

    PersistentFSConnection.closeStorages(recordsStorage, namesStorage, attributesStorage, contentsStorage);
  }

  public void deleteEverything() throws IOException {
    boolean deleted = FileUtil.delete(corruptionMarkerFile.toFile());
    if (!deleted) {
      LOG.info("Can't delete " + corruptionMarkerFile);
    }

    makeBestEffortToCleanStorage(namesStorage, namesFile);

    makeBestEffortToCleanStorage(attributesStorage, attributesFile);

    makeBestEffortToCleanStorage(attributesEnumerator, attributesFile);

    makeBestEffortToCleanStorage(contentsStorage, contentsFile);

    makeBestEffortToCleanStorage(recordsStorage, recordsFile);

    deleted = IOUtil.deleteAllFilesStartingWith(vfsPaths.getRootsBaseFile());
    if (!deleted) {
      LOG.info("Can't delete " + vfsPaths.getRootsBaseFile());
    }
  }

  public PersistentFSConnection createConnection() throws IOException {
    return new PersistentFSConnection(
      vfsPaths,
      recordsStorage,
      namesStorage,
      attributesStorage,
      contentsStorage,
      attributesEnumerator,
      reusableFileIdsLazy,
      new VFSRecoveryInfo(
        problemsRecovered,
        invalidateContentIds,
        directoriesIdsToRefresh,
        filesIdsToInvalidate
      )
    );
  }

  public void selfCheck() throws IOException {
    //VFS errors early on startup cause terrifying UX error messages. It is much better to catch VFS
    // corruption early on and rebuild VFS from 0.
    //But we also don't want to always check each VFS file on startup -- it delays regular startup
    // (i.e. without corruptions) quite a lot.
    //So the tradeoff: we use few heuristics to quickly check for the most likely signs of corruption,
    // and if we find any such sign -- switch to more vigilant checking:

    if (!recordsStorage.wasClosedProperly()) {
      addProblem(NOT_CLOSED_PROPERLY, "VFS wasn't safely shut down: records.wasClosedProperly is false");
    }
    int errorsAccumulated = recordsStorage.getErrorsAccumulated();
    if (errorsAccumulated > 0) {
      addProblem(HAS_ERRORS_IN_PREVIOUS_SESSION, "VFS accumulated " + errorsAccumulated + " errors in last session");
    }

    if (attributesEnumerator.isEmpty() && !attributesStorage.isEmpty()) {
      addProblem(ATTRIBUTES_STORAGE_CORRUPTED, "Attributes enumerator is empty, while attributesStorage is !empty");
    }

    int maxAllocatedID = recordsStorage.maxAllocatedID();

    //Faster to look only for the first error of each kind:
    boolean nameStorageHasErrors = false;
    boolean contentHashesStorageHasErrors = false;
    boolean attributesStorageHasErrors = false;

    //Try to resolve few nameId/contentId/attributeRefId against apt enumerators/storages -- which is quite
    // likely fails if storages/enumerators' files are corrupted anyhow, hence serves as a self-check heuristic.
    //Check _every_ fileId is quite slow, but it is definitely worth checking _some_.
    // So a tradeoff:
    // If there were _no_ signs of corruption -- check only fileId(=)2, 4, 8...) -> always < 32 checks, given fileId
    // is int32.
    // If _any_ signs of corruption arises during quick-scan (or even before it) -> fallback to full scan, check
    // _every_ fileId.
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
        if (!attributesStorageHasErrors) {
          if (!attributeRecordIsValid(fileId)) {
            attributesStorageHasErrors = true;
          }
        }
      }
    }

    //...If there are some errors, or other signs of possible corruption
    // -> fallback to a full scan:
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
        if (!attributesStorageHasErrors) {
          if (!attributeRecordIsValid(fileId)) {
            attributesStorageHasErrors = true;
          }
        }
      }
    }
  }

  private boolean contentResolvedSuccessfully(int fileId) throws IOException {
    int contentId = recordsStorage.getContentRecordId(fileId);
    if (contentId != DataEnumerator.NULL_ID) {
      try {
        contentsStorage.checkRecord(contentId, true);
      }
      catch (Throwable t) {
        addProblem(CONTENT_STORAGES_INCOMPLETE,
                   "file[#" + fileId + "].contentId(=" + contentId + ") failed resolution in contentStorage", t
        );
        return false;
      }
    }

    return true;
  }

  private boolean nameResolvedSuccessfully(int fileId) throws IOException {
    int nameId = recordsStorage.getNameId(fileId);
    if (nameId == DataEnumerator.NULL_ID) {
      return false;
    }
    try {
      String name = namesStorage.valueOf(nameId);
      if (name == null) {
        addProblem(NAME_STORAGE_INCOMPLETE,
                   "file[#" + fileId + "].nameId(=" + nameId + ") is not present in namesEnumerator");
        return false;
      }
      else if (!FSRecordsImpl.USE_FAST_NAMES_IMPLEMENTATION) {
        int reCheckNameId = namesStorage.tryEnumerate(name);
        if (reCheckNameId != nameId) {
          addProblem(NAME_STORAGE_INCOMPLETE,
                     "namesEnumerator is corrupted: file[#" + fileId + "]" +
                     ".nameId(=" + nameId + ") -> [" + name + "] -> tryEnumerate() -> " + reCheckNameId
          );
          return false;
        }
        //Fast (DurableStringEnumerator) implementation persists only forward (id->name) index, and re-build inverse
        // (name->id) index in memory, on each loading, so:
        // 1) no need to check inverse index since it can't be corrupted on disk
        // 2) inverse index is building async, so by trying to check it we force the building and ruin async-ness
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

  private boolean attributeRecordIsValid(int fileId) throws IOException {
    int attributeRecordId = recordsStorage.getAttributeRecordId(fileId);
    if (attributeRecordId == VFSAttributesStorage.NON_EXISTENT_ATTRIBUTE_RECORD_ID) {
      return true;
    }

    try {
      attributesStorage.checkAttributeRecordSanity(fileId, attributeRecordId);
      return true;
    }
    catch (Throwable t) {
      addProblem(ATTRIBUTES_STORAGE_CORRUPTED,
                 "file[#" + fileId + "].attributeRefId(=" + attributeRecordId + "): attributesStorage read failed", t
      );
      return false;
    }
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
           && contentsStorage.isEmpty();
  }


  public @NotNull VFSAttributesStorage createAttributesStorage(@NotNull Path attributesFile) throws IOException {
    if (FSRecordsImpl.USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION) {
      //avg record size is ~60b, hence I've chosen minCapacity=64 bytes, and defaultCapacity= 2*minCapacity
      final SpaceAllocationStrategy allocationStrategy = new DataLengthPlusFixedPercentStrategy(
        /*min: */64, /*default: */ 128,
        /*max: */StreamlinedBlobStorageHelper.MAX_CAPACITY,
        /*percentOnTop: */30
      );
      final StreamlinedBlobStorage blobStorage;
      boolean nativeBytesOrder = true;
      if (FSRecordsImpl.USE_ATTRIBUTES_OVER_NEW_FILE_PAGE_CACHE && PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
        LOG.info("VFS uses streamlined attributes storage (over new FilePageCache)");
        //RC: make page smaller for the transition period: new FPCache has quite a small memory and it's hard
        //    to manage huge 10Mb pages having only ~100-150Mb budget in total, it ruins large-numbers assumptions
        int pageSize = 1 << 20;//PageCacheUtils.DEFAULT_PAGE_SIZE,
        blobStorage = IOUtil.wrapSafely(
          new PagedFileStorageWithRWLockedPageContent(
            attributesFile,
            PERSISTENT_FS_STORAGE_CONTEXT,
            pageSize,
            nativeBytesOrder,
            PageContentLockingStrategy.LOCK_PER_PAGE
          ),
          storage -> new StreamlinedBlobStorageOverLockFreePagedStorage(storage, allocationStrategy)
        );
      }
      else if (FSRecordsImpl.USE_ATTRIBUTES_OVER_MMAPPED_FILE) {
        LOG.info("VFS uses streamlined attributes storage (over mmapped file)");
        int pageSize = 1 << 24;//16Mb
        blobStorage = MMappedFileStorageFactory.withDefaults()
          .pageSize(pageSize)
          //mmapped and !mmapped storages have the same binary layout, so mmapped storage could inherit all the
          // data from non-mmapped -- the only 'migration' needed is to page-align the file:
          .ifFileIsNotPageAligned(EXPAND_FILE)
          .wrapStorageSafely(
            attributesFile,
            storage -> new StreamlinedBlobStorageOverMMappedFile(storage, allocationStrategy)
          );
      }
      else {
        LOG.info("VFS uses streamlined attributes storage (over regular FilePageCache)");
        blobStorage = IOUtil.wrapSafely(
          new PagedFileStorage(
            attributesFile,
            PERSISTENT_FS_STORAGE_CONTEXT,
            PageCacheUtils.DEFAULT_PAGE_SIZE,
            /*valuesAreAligned: */ true,
            nativeBytesOrder
          ),
          storage -> new StreamlinedBlobStorageOverPagedStorage(storage, allocationStrategy)
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

  private @NotNull ScannableDataEnumeratorEx<String> createFileNamesEnumerator(@NotNull Path namesFile) throws IOException {
    if (FSRecordsImpl.USE_FAST_NAMES_IMPLEMENTATION) {
      LOG.info("VFS uses 'fast' names enumerator (over mmapped file)");
      //if we use _same_ namesFile for fast/regular enumerator (which seems natural at a first glance), then
      // on transition, fast enumerator couldn't recognize regular enumerator file format, and throws some
      // bizare exception => VFS is rebuilt, but with rebuildCause=UNRECOGNIZED instead of 'version mismatch'.
      //To get an expected exception on transition, we need regular/fast enumerator to use different files,
      // e.g. 'names.dat' / 'names.dat.mmap'
      Path namesPathEx = Path.of(namesFile + ".mmap");
      return DurableStringEnumerator.openAsync(namesPathEx, executorService);
    }
    else {
      LOG.info("VFS uses regular (btree) names enumerator");
      return new PersistentStringEnumerator(namesFile, PERSISTENT_FS_STORAGE_CONTEXT);
    }
  }

  public @NotNull VFSContentStorage createContentStorage(@NotNull Path contentsHashesFile,
                                                         @NotNull Path contentsFile) throws IOException {
    if (FSRecordsImpl.USE_CONTENT_STORAGE_OVER_MMAPPED_FILE) {
      //Use larger pages: content storage is usually quite big.
      int pageSize = 64 * IOUtil.MiB;

      if (pageSize <= FileUtilRt.LARGE_FOR_CONTENT_LOADING) {
        //pageSize is an upper limit on record size for AppendOnlyLogOverMMappedFile:
        LOG.warn("ContentStorage.pageSize(=" + pageSize + ") " +
                 "must be > FileUtilRt.LARGE_FOR_CONTENT_LOADING(=" + FileUtilRt.LARGE_FOR_CONTENT_LOADING + "b), " +
                 "otherwise large content can't fit");
      }
      CompressingAlgo compressionAlgo = switch (FSRecordsImpl.COMPRESSION_ALGO) {
        case "zip" -> new CompressingAlgo.ZipAlgo(FSRecordsImpl.COMPRESS_CONTENT_IF_LARGER_THAN);
        case "lz4" -> new CompressingAlgo.Lz4Algo(FSRecordsImpl.COMPRESS_CONTENT_IF_LARGER_THAN);
        //"none"
        default -> new CompressingAlgo.NoCompressionAlgo();
      };
      LOG.info("VFS uses content storage over memory-mapped file, with compression algo: " + compressionAlgo);
      return new VFSContentStorageOverMMappedFile(contentsFile, pageSize, compressionAlgo);
    }

    RefCountingContentStorage contentStorage;
    ExecutorService storingPool = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool");
    boolean useContentHashes = true;
    if (FSRecordsImpl.USE_CONTENT_STORAGE_OVER_NEW_FILE_PAGE_CACHE && PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
      LOG.info("VFS uses content storage over new FilePageCache");
      //FiXME RC: now we create storage over new FilePageCache, but protected by the same global lock used by all storages
      //          atop of old page-cache! Which is dummy, since content storage is independent, and could have at least
      //          its own RWLock!
      contentStorage = new RefCountingContentStorageImplLF(contentsFile, FIVE_PERCENT_FOR_GROWTH, storingPool, useContentHashes);
    }
    else {
      LOG.info("VFS uses content storage over regular FilePageCache");
      contentStorage = new RefCountingContentStorageImpl(contentsFile, FIVE_PERCENT_FOR_GROWTH, storingPool, useContentHashes);
    }
    return new ContentStorageAdapter(
      contentStorage,
      () -> openContentHashEnumeratorOrCreateEmpty(contentsHashesFile)
    );
  }


  private static @NotNull ContentHashEnumerator openContentHashEnumeratorOrCreateEmpty(@NotNull Path contentsHashesFile)
    throws IOException {
    try {
      return openContentHashEnumerator(contentsHashesFile);
    }
    catch (IOException e) {
      //No need to fail here: just clean contentHashes, and open empty -- content hashes could be re-build
      // by contentStorage data
      LOG.warn("ContentHashEnumerator is broken -- clean it, hope it will be recovered from ContentStorage later on. " +
               "Cause: " + e.getMessage());
      IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
      return openContentHashEnumerator(contentsHashesFile);
    }
  }

  private static @NotNull ContentHashEnumerator openContentHashEnumerator(@NotNull Path contentsHashesFile) throws IOException {
    if (FSRecordsImpl.USE_CONTENT_HASH_STORAGE_OVER_MMAPPED_FILE) {
      LOG.info("VFS uses content hash storage over mmapped file");
      return ContentHashEnumeratorOverDurableEnumerator.open(contentsHashesFile);
    }
    else {
      LOG.info("VFS uses content hash storage over regular FilePageCache");
      return ContentHashEnumerator.open(contentsHashesFile, PERSISTENT_FS_STORAGE_CONTEXT);
    }
  }

  public @NotNull PersistentFSRecordsStorage createRecordsStorage(@NotNull Path recordsFile) throws IOException {
    StorageFactory<PersistentFSRecordsStorage> recordsStorageFactory = PersistentFSRecordsStorageFactory.storageImplementation();
    LOG.info("VFS uses " + recordsStorageFactory + " storage for main file records table");
    return recordsStorageFactory.wrapStorageSafely(recordsFile, records -> {
      return records;
    });
  }

  /** @return common version of all 3 storages, or -1, if their versions are different (i.e. inconsistent) */
  private static int commonVersionIfExists(@NotNull PersistentFSRecordsStorage records,
                                           @NotNull VFSAttributesStorage attributes,
                                           @NotNull VFSContentStorage contents) throws IOException {
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
                                        @NotNull VFSAttributesStorage attributes,
                                        @NotNull VFSContentStorage contents,
                                        int version) throws IOException {
    records.setVersion(version);
    attributes.setVersion(version);
    contents.setVersion(version);
  }

  private static void makeBestEffortToCleanStorage(@Nullable Object storage,
                                                   @NotNull Path storageFile) {
    if (storage instanceof CleanableStorage) {
      try {
        ((CleanableStorage)storage).closeAndClean();
      }
      catch (Throwable t) {
        LOG.info(storage.getClass().getSimpleName() + ".closeAndClean() fails: " +
                 t.getClass().getSimpleName() + "(" + ExceptionUtil.getNonEmptyMessage(t, "<no error message given>") + ")");
      }
    }
    else {
      LOG.info("[" + storageFile.getFileName() + "]: " + storage + " is not CleanableStorage " +
               "-> trying to clean by explicitly removing all the files [" + storageFile.getFileName() + "*]");
    }
    //If storage fails to open -- we can't use storage to closeAndClean() its on-disk data, because the storage=null.
    // In a perfect world we should setup each storage to clean-if-not-successfully-open -- in a real world we
    // can't be sure all of the storages follow that rule.
    // And if some storage does not follow, it creates infinite cycle: trying to open VFS -> fail -> trying
    // to clean -> fail to clean some storage(s) -> trying to open fresh -> fail again as some storage(s) wasn't
    // cleaned -> repeat.
    //So this branch is still useful to prevent such a cycle.
    boolean noSuchFilesRemains = IOUtil.deleteAllFilesStartingWith(storageFile);
    if (!noSuchFilesRemains) {
      LOG.info("Can't delete " + storageFile + "*");
    }
  }


  //======================== accessors: ============================================================================

  public PersistentFSRecordsStorage recordsStorage() {
    return recordsStorage;
  }

  public ScannableDataEnumeratorEx<String> namesStorage() {
    return namesStorage;
  }

  public VFSAttributesStorage attributesStorage() {
    return attributesStorage;
  }

  public VFSContentStorage contentsStorage() {
    return contentsStorage;
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

  public void setAttributesStorage(VFSAttributesStorage attributesStorage) {
    this.attributesStorage = attributesStorage;
  }

  public void setContentsStorage(VFSContentStorage contentsStorage) {
    this.contentsStorage = contentsStorage;
  }

  public void setAttributesEnumerator(SimpleStringPersistentEnumerator attributesEnumerator) {
    this.attributesEnumerator = attributesEnumerator;
  }


  // ============================== for VFSRecoverer to use: ============================================================

  public List<VFSInitException> problemsDuringLoad() {
    return Collections.unmodifiableList(problemsDuringLoad);
  }

  public List<VFSInitException> problemsDuringLoad(@NotNull VFSInitException.ErrorCategory firstCategory,
                                                   VFSInitException.ErrorCategory @NotNull ... restCategories) {
    EnumSet<VFSInitException.ErrorCategory> categories = EnumSet.of(firstCategory, restCategories);
    return problemsDuringLoad.stream()
      .filter(p -> categories.contains(p.category()))
      .toList();
  }

  public void problemsWereRecovered(@NotNull List<VFSInitException> recovered) {
    problemsDuringLoad.removeAll(recovered);
    problemsRecovered.addAll(recovered);

    String recoveredProblemsList = recovered.stream()
      .map(VFSInitException::category)
      .map(Object::toString)
      .collect(joining());
    String remainingProblemsList = problemsDuringLoad.isEmpty() ?
                                   "no problems" :
                                   problemsDuringLoad.stream()
                                     .map(VFSInitException::category)
                                     .map(Object::toString)
                                     .collect(joining());

    LOG.warn("[VFS load problem]: " +
             recoveredProblemsList + " recovered, " +
             remainingProblemsList + " remain");
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

    LOG.warn("[VFS load problem]: " +
             triedToRecover.stream().map(VFSInitException::category).map(Object::toString).collect(joining()) +
             " recovery attempt fails ('" + message + "')"
    );
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
