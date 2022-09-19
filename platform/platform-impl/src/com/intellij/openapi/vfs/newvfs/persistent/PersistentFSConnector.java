// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentCharSequenceEnumerator;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
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

final class PersistentFSConnector {
  static final Lock ourOpenCloseLock = new ReentrantLock();

  private static final Logger LOG = Logger.getInstance(PersistentFSConnector.class);
  private static final int MAX_INITIALIZATION_ATTEMPTS = 10;
  private static final AtomicInteger INITIALIZATION_COUNTER = new AtomicInteger();
  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT = new StorageLockContext(false, true);

  static @NotNull PersistentFSConnection connect(@NotNull String cachesDir, int version, boolean useContentHashes) {
    ourOpenCloseLock.lock();
    try {
      return init(cachesDir, version, useContentHashes);
    }
    finally {
      ourOpenCloseLock.unlock();
    }
  }

  private static @NotNull PersistentFSConnection init(@NotNull String cachesDir, int expectedVersion, boolean useContentHashes) {
    Exception exception = null;
    for (int i = 0; i < MAX_INITIALIZATION_ATTEMPTS; i++) {
      INITIALIZATION_COUNTER.incrementAndGet();
      Pair<PersistentFSConnection, Exception> pair = tryInit(cachesDir, expectedVersion, useContentHashes);
      exception = pair.getSecond();
      if (exception == null) {
        return pair.getFirst();
      }
    }
    throw new RuntimeException("Can't initialize filesystem storage", exception);
  }

  private static @NotNull Pair<PersistentFSConnection, Exception> tryInit(@NotNull String cachesDir,
                                                                          int expectedVersion,
                                                                          boolean useContentHashes) {
    Storage attributes = null;
    RefCountingContentStorage contents = null;
    PersistentFSRecordsStorage records = null;
    ContentHashEnumerator contentHashesEnumerator = null;
    PersistentCharSequenceEnumerator names = null;
    boolean markDirty = false;

    PersistentFSPaths persistentFSPaths = new PersistentFSPaths(cachesDir);
    Path basePath = new File(cachesDir).getAbsoluteFile().toPath();
    try {
      Files.createDirectories(basePath);
    }
    catch (IOException e) {
      return Pair.create(null, e);
    }

    Path namesFile = basePath.resolve("names" + PersistentFSPaths.VFS_FILES_EXTENSION);
    Path attributesFile = basePath.resolve("attrib" + PersistentFSPaths.VFS_FILES_EXTENSION);
    Path contentsFile = basePath.resolve("content" + PersistentFSPaths.VFS_FILES_EXTENSION);
    Path contentsHashesFile = basePath.resolve("contentHashes" + PersistentFSPaths.VFS_FILES_EXTENSION);
    Path recordsFile = basePath.resolve("records" + PersistentFSPaths.VFS_FILES_EXTENSION);
    Path enumeratedAttributesFile = basePath.resolve("enum_attrib" + PersistentFSPaths.VFS_FILES_EXTENSION);

    File vfsDependentEnumBaseFile = persistentFSPaths.getVfsEnumBaseFile();

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
      names = new PersistentCharSequenceEnumerator(namesFile, PERSISTENT_FS_STORAGE_CONTEXT);

      attributes = new Storage(attributesFile, PersistentFSConnection.REASONABLY_SMALL) {
        @Override
        protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext context, @NotNull Path recordsFile) throws IOException {
          return FSRecords.inlineAttributes && FSRecords.useSmallAttrTable
                 ? new CompactRecordsTable(recordsFile, context, false)
                 : super.createRecordsTable(context, recordsFile);
        }
      };

      contents = new RefCountingContentStorage(contentsFile,
                                               CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
                                               SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
                                               FSRecords.useCompressionUtil,
                                               useContentHashes);

      // sources usually zipped with 4x ratio
      contentHashesEnumerator = useContentHashes ? new ContentHashEnumerator(contentsHashesFile, PERSISTENT_FS_STORAGE_CONTEXT) : null;
      if (contentHashesEnumerator != null) {
        checkContentSanity(contents, contentHashesEnumerator);
      }

      SimpleStringPersistentEnumerator enumeratedAttributes = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);


      records = PersistentFSRecordsStorage.createStorage(recordsFile);

      boolean initial = records.length() == 0;

      if (initial) {
        // Clean header
        records.cleanRecord(0);
        // Create root record
        records.cleanRecord(1);
        setCurrentVersion(records, attributes, contents, expectedVersion);
      }

      int version = getVersion(records, attributes, contents);
      if (version != expectedVersion) {
        throw new IOException("FS repository version mismatch: actual=" + version + " expected=" + FSRecords.getVersion());
      }

      if (records.getConnectionStatus() != PersistentFSHeaders.SAFELY_CLOSED_MAGIC) {
        throw new IOException("FS repository wasn't safely shut down");
      }
      if (initial) {
        markDirty = true;
      }
      IntList freeRecords = new IntArrayList();
      loadFreeRecordsAndInvertedNameIndex(records, freeRecords);
      return Pair.create(new PersistentFSConnection(persistentFSPaths,
                                                    records,
                                                    names,
                                                    attributes,
                                                    contents,
                                                    contentHashesEnumerator,
                                                    enumeratedAttributes,
                                                    freeRecords,
                                                    INITIALIZATION_COUNTER,
                                                    markDirty), null);
    }
    catch (Exception e) { // IOException, IllegalArgumentException
      LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
      try {
        PersistentFSConnection.closeStorages(records, names, attributes, contentHashesEnumerator, contents);

        boolean deleted = FileUtil.delete(persistentFSPaths.getCorruptionMarkerFile());
        deleted &= IOUtil.deleteAllFilesStartingWith(namesFile);
        deleted &= AbstractStorage.deleteFiles(attributesFile);
        deleted &= AbstractStorage.deleteFiles(contentsFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(recordsFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(vfsDependentEnumBaseFile);
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

  private static void checkContentSanity(@NotNull RefCountingContentStorage contents,
                                         @NotNull ContentHashEnumerator contentHashesEnumerator) throws IOException {
    int largestId = contentHashesEnumerator.getLargestId();
    int liveRecordsCount = contents.getRecordsCount();
    if (largestId != liveRecordsCount) {
      throw new IOException("Content storage & enumerator corrupted");
    }
  }

  private static void loadFreeRecordsAndInvertedNameIndex(@NotNull PersistentFSRecordsStorage records,
                                                          @NotNull IntList freeFileIds) throws IOException {
    long start = System.nanoTime();
    InvertedNameIndex.clear();
    records.processAllRecords((fileId, nameId, flags, parentId, corrupted) -> {
      if (BitUtil.isSet(flags, PersistentFS.Flags.FREE_RECORD_FLAG)) {
        freeFileIds.add(fileId);
      }
      else if (nameId != 0) {
        InvertedNameIndex.updateDataInner(fileId, nameId);
      }
    });
    LOG.info(TimeoutUtil.getDurationMillis(start) + " ms to load free records and inverted name index");
  }

  private static int getVersion(PersistentFSRecordsStorage records,
                                Storage attributes,
                                RefCountingContentStorage contents) throws IOException {
    final int recordsVersion = records.getVersion();
    if (attributes.getVersion() != recordsVersion || contents.getVersion() != recordsVersion) return -1;

    return recordsVersion;
  }

  private static void setCurrentVersion(PersistentFSRecordsStorage records,
                                        Storage attributes,
                                        RefCountingContentStorage contents,
                                        int version) throws IOException {
    records.setVersion(version);
    attributes.setVersion(version);
    contents.setVersion(version);
    records.setConnectionStatus(PersistentFSHeaders.SAFELY_CLOSED_MAGIC);
  }
}
