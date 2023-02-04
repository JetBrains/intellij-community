// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor.hasDeletedFlag;

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

  public static @NotNull PersistentFSConnection connect(@NotNull String cachesDir,
                                                        int version,
                                                        boolean useContentHashes,
                                                        List<ConnectionInterceptor> interceptors) {
    ourOpenCloseLock.lock();
    try {
      return init(cachesDir, version, useContentHashes, interceptors);
    }
    finally {
      ourOpenCloseLock.unlock();
    }
  }

  public static void disconnect(@NotNull final PersistentFSConnection connection) {
    ourOpenCloseLock.lock();
    try {
      InvertedNameIndex.clear();
      connection.doForce();
      connection.closeFiles();
    }
    catch (IOException e) {
      connection.handleError(e);
    }
    finally {
      ourOpenCloseLock.unlock();
    }
  }

  //=== internals:

  private static @NotNull PersistentFSConnection init(@NotNull String cachesDir, int expectedVersion, boolean useContentHashes,
                                                      List<ConnectionInterceptor> interceptors) {
    Throwable exception = null;
    for (int i = 0; i < MAX_INITIALIZATION_ATTEMPTS; i++) {
      INITIALIZATION_COUNTER.incrementAndGet();
      Pair<PersistentFSConnection, Throwable> pair = tryInit(cachesDir, expectedVersion, useContentHashes, interceptors);
      exception = pair.getSecond();
      if (exception == null) {
        return pair.getFirst();
      }
    }
    throw new RuntimeException("Can't initialize filesystem storage", exception);
  }

  private static @NotNull Pair<PersistentFSConnection, Throwable> tryInit(@NotNull String cachesDir,
                                                                          int expectedVersion,
                                                                          boolean useContentHashes,
                                                                          List<ConnectionInterceptor> interceptors) {
    AbstractAttributesStorage attributes = null;
    RefCountingContentStorage contents = null;
    PersistentFSRecordsStorage records = null;
    ContentHashEnumerator contentHashesEnumerator = null;
    ScannableDataEnumeratorEx<String> names = null;
    boolean markDirty = false;

    final PersistentFSPaths persistentFSPaths = new PersistentFSPaths(cachesDir);
    final Path basePath = new File(cachesDir).getAbsoluteFile().toPath();
    try {
      Files.createDirectories(basePath);
    }
    catch (IOException e) {
      return Pair.create(null, e);
    }

    final Path namesFile = basePath.resolve("names" + PersistentFSPaths.VFS_FILES_EXTENSION);
    final Path attributesFile = basePath.resolve("attributes" + PersistentFSPaths.VFS_FILES_EXTENSION);
    final Path contentsFile = basePath.resolve("content" + PersistentFSPaths.VFS_FILES_EXTENSION);
    final Path contentsHashesFile = basePath.resolve("contentHashes" + PersistentFSPaths.VFS_FILES_EXTENSION);
    final Path recordsFile = basePath.resolve("records" + PersistentFSPaths.VFS_FILES_EXTENSION);
    final Path enumeratedAttributesFile = basePath.resolve("attributes_enums" + PersistentFSPaths.VFS_FILES_EXTENSION);

    //final File vfsDependentEnumBaseFile = persistentFSPaths.getVfsEnumBaseFile();

    try {
      if (persistentFSPaths.getCorruptionMarkerFile().exists()) {
        throw new IOException("Corruption marker file found");
      }

      boolean traceNumeratorOps = ApplicationManager.getApplication().isUnitTestMode() && PlatformUtils.isFleetBackend();
      if (traceNumeratorOps) {
        try (Stream<Path> files = Files.list(basePath)) {
          List<Path> nameEnumeratorFiles =
            files.filter(p -> p.getFileName().toString().startsWith(namesFile.getFileName().toString())).toList();
          LOG.info("Existing name enumerator files: " + nameEnumeratorFiles);
        }
      }


      names = createFileNamesEnumerator(namesFile);

      attributes = createAttributesStorage(attributesFile);

      contents = new RefCountingContentStorageImpl(
        contentsFile,
        CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
        useContentHashes
      );

      // sources usually zipped with 4x ratio
      if (useContentHashes) {
        contentHashesEnumerator = new ContentHashEnumerator(contentsHashesFile, PERSISTENT_FS_STORAGE_CONTEXT);
        checkStoragesAreConsistent(contents, contentHashesEnumerator);
      }
      else {
        contentHashesEnumerator = null;
      }

      //TODO RC: since we bump FSRecords version anyway, I'd like to have completely new Enumerator here:
      //         1. Without legacy issues with null vs 'null' strings
      //         2. With explicit id in file
      //         3. With CopyOnWrite concurrent strategy (hence very cheap .enumerate() for already enumerated values)
      final SimpleStringPersistentEnumerator enumeratedAttributes = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);


      records = PersistentFSRecordsStorageFactory.createStorage(recordsFile);

      final boolean needInitialization = (records.recordsCount() == 0);

      if (needInitialization) {
        // Create root record:
        final int rootRecordId = records.allocateRecord();
        assert (rootRecordId == FSRecords.ROOT_FILE_ID)
          : "First record created must have id=" + FSRecords.ROOT_FILE_ID + " but " + rootRecordId + " got";
        records.cleanRecord(rootRecordId);

        setCurrentVersion(records, attributes, contents, expectedVersion);
      }
      final int version = getVersion(records, attributes, contents);
      if (version != expectedVersion) {
        throw new IOException("FS repository version mismatch: actual=" + version + " expected=" + FSRecords.getVersion());
      }

      if (records.getConnectionStatus() != PersistentFSHeaders.SAFELY_CLOSED_MAGIC) {
        throw new IOException("FS repository wasn't safely shut down");
      }
      if (needInitialization) {
        markDirty = true;
      }
      final IntList freeRecords = new IntArrayList();
      loadFreeRecordsAndInvertedNameIndex(records, freeRecords);
      return Pair.create(new PersistentFSConnection(persistentFSPaths,
                                                    records,
                                                    names,
                                                    attributes,
                                                    contents,
                                                    contentHashesEnumerator,
                                                    enumeratedAttributes,
                                                    freeRecords,
                                                    markDirty,
                                                    interceptors), null);
    }
    catch (Exception | AssertionError e) { // IOException, IllegalArgumentException, AssertionError (assert)
      LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
      try {
        PersistentFSConnection.closeStorages(records, names, attributes, contentHashesEnumerator, contents);

        boolean deleted = FileUtil.delete(persistentFSPaths.getCorruptionMarkerFile());
        deleted &= IOUtil.deleteAllFilesStartingWith(namesFile);
        if (FSRecordsImpl.USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION) {
          deleted &= AttributesStorageOverBlobStorage.deleteStorageFiles(attributesFile);
        }
        else {
          deleted &= AbstractStorage.deleteFiles(attributesFile);
        }
        deleted &= AbstractStorage.deleteFiles(contentsFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(recordsFile);
        //deleted &= IOUtil.deleteAllFilesStartingWith(vfsDependentEnumBaseFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(persistentFSPaths.getRootsBaseFile());
        deleted &= IOUtil.deleteAllFilesStartingWith(enumeratedAttributesFile);

        //if (!deleted) {
        //  throw new IOException("Cannot delete filesystem storage files");
        //}
      }
      catch (IOException e1) {
        e1.addSuppressed(e);
        LOG.warn("Cannot rebuild filesystem storage", e1);
        return Pair.create(null, e1);
      }

      return Pair.create(null, e);
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
          protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext context, @NotNull Path recordsFile)
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
      throw new IOException("Content storage & enumerator corrupted");
    }
  }

  private static void loadFreeRecordsAndInvertedNameIndex(final @NotNull PersistentFSRecordsStorage records,
                                                          final @NotNull IntList freeFileIds) throws IOException {
    final long startedAtNs = System.nanoTime();
    InvertedNameIndex.clear();
    records.processAllRecords((fileId, nameId, flags, parentId, corrupted) -> {
      if (hasDeletedFlag(flags)) {
        freeFileIds.add(fileId);
      }
      else if (nameId != InvertedNameIndex.NULL_NAME_ID) {
        InvertedNameIndex.updateDataInner(fileId, nameId);
      }
    });
    LOG.info(TimeoutUtil.getDurationMillis(startedAtNs) + " ms to load free records and inverted name index");
  }

  /** @return consistent version of all 3 storages, or -1, if their versions are differ (i.e. inconsistent) */
  private static int getVersion(final @NotNull PersistentFSRecordsStorage records,
                                final @NotNull AbstractAttributesStorage attributes,
                                final @NotNull RefCountingContentStorage contents) throws IOException {
    final int recordsVersion = records.getVersion();
    if (attributes.getVersion() != recordsVersion || contents.getVersion() != recordsVersion) return -1;

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
}
