// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Static helper responsible for 'connecting' (opening, initializing) {@linkplain PersistentFSConnection} object,
 * and closing it. It does a few tries to initialize VFS storages, tries to correct/rebuild broken parts, and so on.
 */
final class PersistentFSConnector {
  private static final Lock ourOpenCloseLock = new ReentrantLock();

  private static final Logger LOG = Logger.getInstance(PersistentFSConnector.class);

  private static final int MAX_INITIALIZATION_ATTEMPTS = 10;
  private static final AtomicInteger INITIALIZATION_COUNTER = new AtomicInteger();

  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT = new StorageLockContext(false, true);

  public static @NotNull PersistentFSConnection connect(final @NotNull Path cachesDir,
                                                        final int version,
                                                        final boolean useContentHashes,
                                                        final @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndex,
                                                        final List<ConnectionInterceptor> interceptors) {
    ourOpenCloseLock.lock();
    try {
      return init(cachesDir, version, useContentHashes, invertedNameIndex, interceptors);
    }
    finally {
      ourOpenCloseLock.unlock();
    }
  }

  public static void disconnect(@NotNull PersistentFSConnection connection) throws IOException {
    ourOpenCloseLock.lock();
    try {
      connection.doForce();
      connection.closeFiles();
    }
    finally {
      ourOpenCloseLock.unlock();
    }
  }

  //=== internals:

  private static @NotNull PersistentFSConnection init(final @NotNull Path cachesDir,
                                                      final int expectedVersion,
                                                      final boolean useContentHashes,
                                                      final @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndexToFill,
                                                      final List<ConnectionInterceptor> interceptors) {
    Exception exception = null;
    for (int i = 0; i < MAX_INITIALIZATION_ATTEMPTS; i++) {
      INITIALIZATION_COUNTER.incrementAndGet();
      try {
        return tryInit(cachesDir, expectedVersion, useContentHashes, invertedNameIndexToFill, interceptors);
      }
      catch (IOException e) {
        LOG.info("Init VFS attempt #" + i + " failed: " + e.getMessage());

        if (exception == null) {
          exception = e;
        }
        else {
          exception.addSuppressed(e);
        }
      }
    }
    throw new RuntimeException("VFS can't be initialized (" + MAX_INITIALIZATION_ATTEMPTS + " attempts failed)", exception);
  }

  @VisibleForTesting
  static @NotNull PersistentFSConnection tryInit(final @NotNull Path cachesDir,
                                                 final int expectedVersion,
                                                 final boolean useContentHashes,
                                                 final @NotNull InvertedNameIndex invertedNameIndexToFill,
                                                 final List<ConnectionInterceptor> interceptors) throws IOException {
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
        final List<String> corruptionCause = Files.readAllLines(corruptionMarkerFile, UTF_8);
        throw new IOException("Corruption marker file found\n\tcontent: " + corruptionCause);
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

      //TODO RC: I'd like to have completely new Enumerator here:
      //         1. Without legacy issues with null vs 'null' strings
      //         2. With explicit 'id' stored in a file (instead of implicit id=row num)
      //         3. With CopyOnWrite concurrent strategy (hence very cheap .enumerate() for already enumerated values)
      //         ...next time we bump FSRecords version
      final SimpleStringPersistentEnumerator attributesEnumerator = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);


      recordsStorage = PersistentFSRecordsStorageFactory.createStorage(recordsFile);

      LOG.info("VFS: impl (expected) version=" + expectedVersion +
               ", " + recordsStorage.recordsCount() + " file records" +
               ", " + contentsStorage.getRecordsCount() + " content blobs");

      ensureConsistentVersion(expectedVersion, attributesStorage, contentsStorage, recordsStorage);

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
          throw new IOException("FS repository wasn't safely shut down: records.connectionStatus != SAFELY_CLOSED");
        }
      }

      final IntList freeRecords = new IntArrayList();
      loadFreeRecordsAndInvertedNameIndex(recordsStorage, freeRecords, invertedNameIndexToFill);
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

  private static void ensureConsistentVersion(final int currentImplVersion,
                                              final @NotNull AbstractAttributesStorage attributesStorage,
                                              final @NotNull RefCountingContentStorage contentsStorage,
                                              final @NotNull PersistentFSRecordsStorage recordsStorage) throws IOException {
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
      throw new IOException(
        "FS repository detected version(=" + commonVersion + ") != current version(=" + currentImplVersion + ") -> VFS needs rebuild");
    }
  }

  private static AbstractAttributesStorage createAttributesStorage(final Path attributesFile) throws IOException {
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
      return new AttributesStorageOld(
        /*bulk attribute support: */false,
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
  private static ScannableDataEnumeratorEx<String> createFileNamesEnumerator(final Path namesFile) throws IOException {
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
      throw new IOException("Content storage & enumerator corrupted: " +
                            "contents.records(=" + liveRecordsCount + ") != contentHashes.largestId(=" + largestId + ")");
    }
  }

  private static void loadFreeRecordsAndInvertedNameIndex(final @NotNull PersistentFSRecordsStorage records,
                                                          final @NotNull /*OutParam*/ IntList freeFileIds,
                                                          final @NotNull /*OutParam*/ InvertedNameIndex invertedNameIndexToFill)
    throws IOException {
    final long startedAtNs = System.nanoTime();
    records.processAllRecords((fileId, nameId, flags, parentId, corrupted) -> {
      if (hasDeletedFlag(flags)) {
        freeFileIds.add(fileId);
      }
      else if (nameId != InvertedNameIndex.NULL_NAME_ID) {
        invertedNameIndexToFill.updateDataInner(fileId, nameId);
      }
    });
    LOG.info(TimeoutUtil.getDurationMillis(startedAtNs) + " ms to load free records and inverted name index");
  }

  /** @return common version of all 3 storages, or -1, if their versions are differ (i.e. inconsistent) */
  private static int commonVersionIfExists(final @NotNull PersistentFSRecordsStorage records,
                                           final @NotNull AbstractAttributesStorage attributes,
                                           final @NotNull RefCountingContentStorage contents) throws IOException {
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

  private static void setCurrentVersion(final @NotNull PersistentFSRecordsStorage records,
                                        final @NotNull AbstractAttributesStorage attributes,
                                        final @NotNull RefCountingContentStorage contents,
                                        final int version) throws IOException {
    records.setVersion(version);
    attributes.setVersion(version);
    contents.setVersion(version);
    records.setConnectionStatus(PersistentFSHeaders.SAFELY_CLOSED_MAGIC);
  }

  private static void logNameEnumeratorFiles(final Path basePath,
                                             final Path namesFile) throws IOException {
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
}
