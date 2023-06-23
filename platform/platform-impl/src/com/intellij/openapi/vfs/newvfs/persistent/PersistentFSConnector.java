// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.OffsetBasedNonStrictStringsEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeSizeStreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage;
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor;
import com.intellij.util.PlatformUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSNeedsRebuildException.RebuildCause.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Static helper responsible for 'connecting' (opening, initializing) {@linkplain PersistentFSConnection} object,
 * and closing it. It does a few tries to initialize VFS storages, tries to correct/rebuild broken parts, and so on.
 */
final class PersistentFSConnector {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnector.class);

  private static final int MAX_INITIALIZATION_ATTEMPTS = 10;

  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT = new StorageLockContext(false, true);

  private static final Lock connectDisconnectLock = new ReentrantLock();


  public static @NotNull PersistentFSConnector.InitializationResult connect(@NotNull Path cachesDir,
                                                                            int version,
                                                                            boolean useContentHashes,
                                                                            @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndex,
                                                                            List<ConnectionInterceptor> interceptors) {
    connectDisconnectLock.lock();
    try {
      return init(cachesDir, version, useContentHashes, invertedNameIndex, interceptors);
    }
    finally {
      connectDisconnectLock.unlock();
    }
  }

  public static void disconnect(@NotNull PersistentFSConnection connection) throws IOException {
    connectDisconnectLock.lock();
    try {
      connection.doForce();
      connection.closeFiles();
    }
    finally {
      connectDisconnectLock.unlock();
    }
  }

  //=== internals:

  private static @NotNull PersistentFSConnector.InitializationResult init(@NotNull Path cachesDir,
                                                                          int expectedVersion,
                                                                          boolean useContentHashes,
                                                                          @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndexToFill,
                                                                          List<ConnectionInterceptor> interceptors) {
    List<Throwable> attemptsFailures = new ArrayList<>();
    long initializationStartedNs = System.nanoTime();
    for (int attempt = 0; attempt < MAX_INITIALIZATION_ATTEMPTS; attempt++) {
      try {
        PersistentFSConnection connection = tryInit(
          cachesDir,
          expectedVersion,
          useContentHashes,
          invertedNameIndexToFill,
          interceptors
        );
        //heuristics: just created VFS contains only 1 record (=super-root)
        boolean justCreated = connection.getRecords().recordsCount() == 1 && connection.isDirty();

        return new InitializationResult(
          connection,
          justCreated,
          attemptsFailures,
          System.nanoTime() - initializationStartedNs
        );
      }
      catch (Exception e) {
        LOG.info("Init VFS attempt #" + attempt + " failed: " + e.getMessage());

        attemptsFailures.add(e);
      }
    }

    final RuntimeException fail = new RuntimeException("VFS can't be initialized (" + MAX_INITIALIZATION_ATTEMPTS + " attempts failed)");
    for (Throwable failure : attemptsFailures) {
      fail.addSuppressed(failure);
    }
    throw fail;
  }

  @VisibleForTesting
  static @NotNull PersistentFSConnection tryInit(@NotNull Path cachesDir,
                                                 int currentImplVersion,
                                                 boolean useContentHashes,
                                                 @NotNull InvertedNameIndex invertedNameIndexToFill,
                                                 List<ConnectionInterceptor> interceptors) throws IOException {
    //RC: Mental model behind VFS initialization:
    //   VFS consists of few different storages: records, attributes, content... Each storage has its own on-disk
    //   data format. Each storage also has a writeable header field .version, which is (must be) 0 by default.
    //   VFS as a whole defines 'implementation version' (currentImplVersion) which is the overall on-disk data
    //   format known to current VFS code.
    //
    //   When VFS is initialized from scratch, this 'VFS impl version' is stamped into each storage .version header field.
    //   When VFS is loaded (from already existing on-disk representation), we load each storage from apt file(s), collect
    //   all the storage.versions and check them against currentImplVersion -- and if all the versions are equal to
    //   currentImplVersion => VFS is OK and ready to use ('success path')
    //
    //   If not all versions are equal to currentImplVersion => we assume VFS is outdated, drop all the VFS files, and throw
    //   exception, which most likely triggers VFS rebuild from 0 somewhere above.
    //   Now, versions could be != currentImplVersion for different reasons:
    //   1) On-disk VFS are of an ancient version -- the most obvious reason
    //   2) Some of the VFS storages are missed on disk, and initialized from 0, thus having their .version=0
    //   3) Some of the VFS storages have an unrecognizable on-disk format (e.g. corrupted) -- in this case
    //      specific storage implementation could either
    //      a) re-create storage from 0 => will have .version=0, see branch #2
    //      b) throw an exception => will be caught, and VFS files will be all dropped,
    //                               and likely lead to VFS rebuild from 0 somewhere upper the callstack
    //   So the simple condition (all storages .version must be == currentImplVersion) actually covers a set of different cases
    //   which is convenient.
    //
    //MAYBE RC: Current model has important drawback: it is all-or-nothing, i.e. even a minor change in VFS on-disk format requires
    //          full VFS rebuild. VFS rebuild itself is not so costly -- but it invalidates all fileIds, which causes Indexes rebuild,
    //          which IS costly.
    //          It would be nicer if VFS be able to just 'upgrade' a minor change in format to a newer version, without full rebuild
    //          -- but with current approach such functionality it is hard to plug in.
    //          Sketch of a better implementation:
    //          1) VFS currentImplVersion is stored in a single dedicated place, in 'version.txt' file (human-readable)
    //          2) Each VFS storage manages its own on-disk-format-version -- i.e. each storage has its own CURRENT_IMPL_VERSION,
    //             which it stores somewhere in a file(s) header. And each storage is responsible for detecting on-disk version,
    //             and either read the known format, or read-and-silently-upgrade known but slightly outdated format, or throw
    //             an error if it can't deal with on-disk data at all.
    //          VFS.currentImplVersion is changed only on a major format changes, there implement 'upgrade' is too costly, and full
    //          rebuild is the way to go. Another reason for full rebuild is if any of storages is completely confused by on-disk
    //          data (=likely corruption or too old data format). In other cases VFS could be upgraded 'under the carpet' without
    //          invalidating fileId, hence Indexes don't need to be rebuild.

    AbstractAttributesStorage attributesStorage = null;
    RefCountingContentStorage contentsStorage = null;
    PersistentFSRecordsStorage recordsStorage = null;
    ContentHashEnumerator contentHashesEnumerator = null;
    ScannableDataEnumeratorEx<String> namesStorage = null;

    final PersistentFSPaths persistentFSPaths = new PersistentFSPaths(cachesDir);
    final Path basePath = cachesDir.toAbsolutePath();
    Files.createDirectories(basePath);

    final Path namesFile = persistentFSPaths.storagePath("names");
    final Path attributesFile = persistentFSPaths.storagePath("attributes");
    final Path contentsFile = persistentFSPaths.storagePath("content");
    final Path contentsHashesFile = persistentFSPaths.storagePath("contentHashes");
    final Path recordsFile = persistentFSPaths.storagePath("records");
    final Path enumeratedAttributesFile = persistentFSPaths.storagePath("attributes_enums");

    final Path corruptionMarkerFile = persistentFSPaths.getCorruptionMarkerFile();
    try {
      if (Files.exists(corruptionMarkerFile)) {
        // TODO on vfs corruption vfslog must be erased because enumerators are lost (force compaction to erase everything)
        final List<String> corruptionCause = Files.readAllLines(corruptionMarkerFile, UTF_8);
        throw new VFSNeedsRebuildException(SCHEDULED_REBUILD, "Corruption marker file found\n\tcontent: " + corruptionCause);
      }

      logNameEnumeratorFiles(basePath, namesFile);

      namesStorage = createFileNamesEnumerator(namesFile);

      attributesStorage = createAttributesStorage(attributesFile);

      contentsStorage = new RefCountingContentStorageImpl(
        contentsFile,
        CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
        useContentHashes
      );

      // sources usually zipped with 4x ratio
      if (useContentHashes) {
        contentHashesEnumerator = new ContentHashEnumerator(contentsHashesFile, PERSISTENT_FS_STORAGE_CONTEXT);
        checkStoragesAreConsistent(contentsStorage, contentHashesEnumerator);
      }
      else {
        contentHashesEnumerator = null;
      }

      //MAYBE RC: I'd like to have completely new Enumerator here:
      //          1. Without legacy issues with null vs 'null' strings
      //          2. With explicit 'id' stored in a file (instead of implicit id=row num)
      //          3. With CopyOnWrite concurrent strategy (hence very cheap .enumerate() for already enumerated values)
      //          ...next time we bump FSRecords version
      final SimpleStringPersistentEnumerator attributesEnumerator = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);


      recordsStorage = PersistentFSRecordsStorageFactory.createStorage(recordsFile);

      LOG.info("VFS: impl (expected) version=" + currentImplVersion +
               ", " + recordsStorage.recordsCount() + " file records" +
               ", " + contentsStorage.getRecordsCount() + " content blobs");

      ensureConsistentVersion(currentImplVersion, attributesStorage, contentsStorage, recordsStorage);

      final boolean needInitialization = (recordsStorage.recordsCount() == 0);

      if (needInitialization) {
        // Create root record:
        final int rootRecordId = recordsStorage.allocateRecord();
        if (rootRecordId != FSRecords.ROOT_FILE_ID) {
          throw new AssertionError("First record created must have id=" + FSRecords.ROOT_FILE_ID + " but " + rootRecordId + " got instead");
        }
        recordsStorage.cleanRecord(rootRecordId);
      }
      else {
        if (recordsStorage.getConnectionStatus() != PersistentFSHeaders.SAFELY_CLOSED_MAGIC) {
          throw new VFSNeedsRebuildException(NOT_CLOSED_PROPERLY,
                                             "FS repository wasn't safely shut down: records.connectionStatus != SAFELY_CLOSED");
        }
      }

      final IntList freeRecords = new IntArrayList();
      selfCheckAndLoadFreeRecordsAndNameIndex(
        recordsStorage, namesStorage, contentHashesEnumerator, attributesStorage, attributesEnumerator,
        freeRecords, invertedNameIndexToFill
      );
      final PersistentFSConnection connection = new PersistentFSConnection(
        persistentFSPaths,
        recordsStorage,
        namesStorage,
        attributesStorage,
        contentsStorage,
        contentHashesEnumerator,
        attributesEnumerator,
        freeRecords,
        interceptors
      );

      if (needInitialization) {//just-initialized connection is dirty (i.e. must be saved)
        connection.markDirty();
      }

      return connection;
    }
    catch (Exception | AssertionError e) { // IOException, IllegalArgumentException, AssertionError (assert)
      LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
      try {
        PersistentFSConnection.closeStorages(recordsStorage, namesStorage, attributesStorage, contentHashesEnumerator, contentsStorage);

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
        deleted = IOUtil.deleteAllFilesStartingWith(persistentFSPaths.getRootsBaseFile());
        if (!deleted) {
          LOG.info("Can't delete " + persistentFSPaths.getRootsBaseFile());
        }
        deleted = IOUtil.deleteAllFilesStartingWith(enumeratedAttributesFile);
        if (!deleted) {
          LOG.info("Can't delete " + enumeratedAttributesFile);
        }
      }
      catch (IOException e1) {
        e1.addSuppressed(e);
        LOG.warn("Cannot clean filesystem storage", e1);
        throw e1;
      }

      throw e;
    }
  }

  private static void ensureConsistentVersion(int currentImplVersion,
                                              @NotNull AbstractAttributesStorage attributesStorage,
                                              @NotNull RefCountingContentStorage contentsStorage,
                                              @NotNull PersistentFSRecordsStorage recordsStorage) throws IOException {
    if (currentImplVersion == 0) {
      throw new IllegalArgumentException("currentImplVersion(=" + currentImplVersion + ") must be != 0");
    }
    //Versions of different storages should be either all set, and all == currentImplementationVersion,
    // or none set at all (& storages are all fresh & empty) -- everything but that is a version mismatch
    // and a trigger for VFS rebuild:

    final int commonVersion = commonVersionIfExists(recordsStorage, attributesStorage, contentsStorage);

    if (commonVersion != currentImplVersion) {
      //If storages are just created -> commonVersion=0, and storages are empty.
      // => we should stamp them with current implVersion and go ahead.
      //Otherwise it is version mismatch and we should rebuild VFS storages from 0.
      final boolean storagesAreEmpty = (recordsStorage.recordsCount() == 0);
      if (commonVersion == 0 && storagesAreEmpty) {//MAYBE RC: better check also attributes/contentsStorage.isEmpty()?
        //all storages are fresh new => assign their versions to the current one:
        setCurrentVersion(recordsStorage, attributesStorage, contentsStorage, currentImplVersion);
        return;
      }

      //if commonVersion > 0 => current VFS data has a consistent version, but != implVersion
      //                     => IMPL_VERSION_MISMATCH
      //if commonVersion = -1 => VFS data has an inconsistent versions
      //                      => DATA_INCONSISTENT
      final VFSNeedsRebuildException.RebuildCause rebuildCause = commonVersion > 0 ? IMPL_VERSION_MISMATCH : DATA_INCONSISTENT;
      throw new VFSNeedsRebuildException(
        rebuildCause,
        "FS repository detected version(=" + commonVersion + ") != current version(=" + currentImplVersion + ") -> VFS needs rebuild"
      );
    }
  }

  private static AbstractAttributesStorage createAttributesStorage(@NotNull Path attributesFile) throws IOException {
    if (FSRecordsImpl.USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION) {
      LOG.info("VFS uses new (streamlined) attributes storage");
      //avg record size is ~60b, hence I've chosen minCapacity=64 bytes, and defaultCapacity= 2*minCapacity
      final DataLengthPlusFixedPercentStrategy allocationStrategy = new DataLengthPlusFixedPercentStrategy(128, 64, 30);
      final StreamlinedBlobStorage blobStorage;
      if (PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
        blobStorage = new StreamlinedBlobStorageOverLockFreePagesStorage(
          new PagedFileStorageLockFree(attributesFile, PERSISTENT_FS_STORAGE_CONTEXT, PageCacheUtils.DEFAULT_PAGE_SIZE, true),
          allocationStrategy
        );
      }
      else {
        blobStorage = new LargeSizeStreamlinedBlobStorage(
          new PagedFileStorage(attributesFile, PERSISTENT_FS_STORAGE_CONTEXT, PageCacheUtils.DEFAULT_PAGE_SIZE, true, true),
          allocationStrategy
        );
      }
      return new AttributesStorageOverBlobStorage(blobStorage);
    }
    else {
      LOG.info("VFS uses regular attributes storage");
      boolean bulkAttrReadSupport = false;
      return new AttributesStorageOld(
        bulkAttrReadSupport,
        FSRecordsImpl.INLINE_ATTRIBUTES,
        new Storage(attributesFile, PersistentFSConnection.REASONABLY_SMALL) {
          @Override
          protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext context,
                                                            @NotNull Path recordsFile)
            throws IOException {
            return FSRecordsImpl.INLINE_ATTRIBUTES && FSRecordsImpl.USE_SMALL_ATTR_TABLE
                   ? new CompactRecordsTable(recordsFile, context, false)
                   : super.createRecordsTable(context, recordsFile);
          }
        });
    }
  }

  @NotNull
  private static ScannableDataEnumeratorEx<String> createFileNamesEnumerator(@NotNull Path namesFile) throws IOException {
    if (FSRecordsImpl.USE_FAST_NAMES_IMPLEMENTATION) {
      LOG.info("VFS uses non-strict names enumerator");
      final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
        namesFile,
        10 * IOUtil.MiB,
        PERSISTENT_FS_STORAGE_CONTEXT,
        IOUtil.MiB,
        false
      );
      return new OffsetBasedNonStrictStringsEnumerator(mappedFile);
    }
    else {
      LOG.info("VFS uses strict names enumerator");
      return new PersistentStringEnumerator(namesFile, PERSISTENT_FS_STORAGE_CONTEXT);
    }
  }

  private static void checkStoragesAreConsistent(@NotNull RefCountingContentStorage contents,
                                                 @NotNull ContentHashEnumerator contentHashesEnumerator) throws IOException {
    int largestId = contentHashesEnumerator.getLargestId();
    int liveRecordsCount = contents.getRecordsCount();
    if (largestId != liveRecordsCount) {
      throw new VFSNeedsRebuildException(
        DATA_INCONSISTENT,
        "Content storage & enumerator corrupted: contents.records(=" + liveRecordsCount + ") != contentHashes.largestId(=" + largestId + ")"
      );
    }
  }

  private static void selfCheckAndLoadFreeRecordsAndNameIndex(@NotNull PersistentFSRecordsStorage records,
                                                              @NotNull ScannableDataEnumeratorEx<String> namesEnumerator,
                                                              @Nullable ContentHashEnumerator contentHashesEnumerator,
                                                              @NotNull AbstractAttributesStorage attributesStorage,
                                                              @NotNull SimpleStringPersistentEnumerator attributesEnumerator,
                                                              @NotNull /*OutParam*/ IntList freeFileIds,
                                                              @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndexToFill)
    throws IOException {
    final long startedAtNs = System.nanoTime();
    try {
      //VFS errors early on startup cause terrifying UX error messages -- it is much better to catch VFS
      // corruption early on and rebuild VFS from 0. But we also don't want to always check each VFS file
      // on startup, since it delays regular startup (i.e. without corruptions) quite a lot.
      // A tradeoff between those two goals: check only fileId=1, 2, 4, 8... log(maxID) total checks,
      // i.e. always < 32 checks, given fileId is int.
      IntRef nextPowFileIdToCheck = new IntRef(0);

      records.processAllRecords((fileId, nameId, flags, parentId, attributeRecordId, contentId, corrupted) -> {
        if (hasDeletedFlag(flags)) {
          freeFileIds.add(fileId);
          return;
        }

        if (nameId != InvertedNameIndex.NULL_NAME_ID) {
          invertedNameIndexToFill.updateDataInner(fileId, nameId);
        }

        if (fileId >> nextPowFileIdToCheck.get() == 1) {
          nextPowFileIdToCheck.inc();
          //Try to resolve few nameId/contentId against apt enumerators -- which is quite likely fails
          //if enumerators' files are corrupted anyhow, hence serves as a self-check heuristic:

          if (nameId != DataEnumeratorEx.NULL_ID) {
            try {
              final String name = namesEnumerator.valueOf(nameId);
              if (name == null) {
                throw new IllegalStateException("file[#" + fileId + "].nameId(=" + nameId + ") is not present in namesEnumerator");
              }
              final int reCheckNameId = namesEnumerator.tryEnumerate(name);
              if (reCheckNameId != nameId) {
                throw new IllegalStateException(
                  "namesEnumerator is corrupted: file[#" + fileId + "]" +
                  ".nameId(=" + nameId + ") -> [" + name + "] -> tryEnumerate() -> " + reCheckNameId
                );
              }
            }
            catch (IOException e) {
              throw new UncheckedIOException("file[#" + fileId + "].nameId(=" + nameId + ") failed resolution in namesEnumerator", e);
            }
          }

          if (contentHashesEnumerator != null
              && contentId != DataEnumeratorEx.NULL_ID) {
            try {
              final byte[] contentHash = contentHashesEnumerator.valueOf(contentId);
              if (contentHash == null) {
                throw new IllegalStateException(
                  "file[#" + fileId + "].contentId(=" + contentId + ") is not present in contentHashesEnumerator"
                );
              }
              final int reCheckContentId = contentHashesEnumerator.tryEnumerate(contentHash);
              if (reCheckContentId != contentId) {
                throw new IllegalStateException(
                  "contentHashesEnumerator is corrupted: file[#" + fileId + "]" +
                  ".contentId(=" + contentId + ") -> [" + Arrays.toString(contentHash) + "] -> tryEnumerate() -> " + reCheckContentId
                );
              }
            }
            catch (IOException e) {
              throw new UncheckedIOException(
                "file[#" + fileId + "].contentId(=" + contentId + ") failed resolution in contentHashesEnumerator", e);
            }
          }
        }
      });
    }
    catch (Throwable t) {
      throw new VFSNeedsRebuildException(
        DATA_INCONSISTENT,
        "VFS self-check: failed",
        t
      );
    }

    LOG.info(TimeoutUtil.getDurationMillis(startedAtNs) + " ms to self-checks, load free records and inverted name index");
  }

  /** @return common version of all 3 storages, or -1, if their versions are differ (i.e. inconsistent) */
  private static int commonVersionIfExists(@NotNull PersistentFSRecordsStorage records,
                                           @NotNull AbstractAttributesStorage attributes,
                                           @NotNull RefCountingContentStorage contents) throws IOException {
    final int recordsVersion = records.getVersion();
    final int attributesVersion = attributes.getVersion();
    final int contentsVersion = contents.getVersion();
    if (attributesVersion != recordsVersion || contentsVersion != recordsVersion) {
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

  private static void logNameEnumeratorFiles(@NotNull Path basePath,
                                             @NotNull Path namesFile) throws IOException {
    final Application application = ApplicationManager.getApplication();
    final boolean traceNumeratorOps = application != null
                                      && application.isUnitTestMode()
                                      && PlatformUtils.isFleetBackend();
    if (traceNumeratorOps) {
      final String namesFilePrefix = namesFile.getFileName().toString();
      try (Stream<Path> files = Files.list(basePath)) {
        List<Path> nameEnumeratorFiles = files
          .filter(p -> p.getFileName().toString().startsWith(namesFilePrefix))
          .toList();
        LOG.info("Existing name enumerator files: " + nameEnumeratorFiles);
      }
    }
  }

  public static class InitializationResult {
    public final boolean storagesCreatedAnew;
    public final @NotNull List<Throwable> attemptsFailures;
    public final @NotNull PersistentFSConnection connection;
    public final long totalInitializationDurationNs;

    private InitializationResult(@NotNull PersistentFSConnection connection,
                                 boolean createdAnew,
                                 @NotNull List<Throwable> attemptsFailures,
                                 long totalInitializationDurationNs) {
      this.connection = connection;
      this.storagesCreatedAnew = createdAnew;
      this.attemptsFailures = attemptsFailures;
      this.totalInitializationDurationNs = totalInitializationDurationNs;
    }
  }
}
