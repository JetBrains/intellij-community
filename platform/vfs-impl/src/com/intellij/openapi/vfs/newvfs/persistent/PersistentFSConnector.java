// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BitUtil;
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
import java.util.concurrent.atomic.AtomicInteger;

final class PersistentFSConnector {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnector.class);
  private static final int MAX_INITIALIZATION_ATTEMPTS = 10;
  private static final AtomicInteger localModificationCounter = new AtomicInteger();

  static @NotNull PersistentFSConnection connect(@NotNull String cachesDir, int version, boolean useContentHashes) {
    return FSRecords.writeAndHandleErrors(() -> {
      return init(cachesDir, version, useContentHashes);
    });
  }

  private static @NotNull PersistentFSConnection init(@NotNull String cachesDir, int expectedVersion, boolean useContentHashes) {
    Exception exception = null;
    for (int i = 0; i < MAX_INITIALIZATION_ATTEMPTS; i++) {
      localModificationCounter.incrementAndGet();
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
    PersistentStringEnumerator names = null;
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

    if (!Files.exists(namesFile)) {
      invalidateIndex("'" + namesFile + "' does not exist");
    }

    try {
      if (persistentFSPaths.getCorruptionMarkerFile().exists()) {
        invalidateIndex("corruption marker found");
        throw new IOException("Corruption marker file found");
      }

      StorageLockContext storageLockContext = new StorageLockContext(false);
      names = new PersistentStringEnumerator(namesFile, storageLockContext);

      attributes = new Storage(attributesFile, PersistentFSConnection.REASONABLY_SMALL) {
        @Override
        protected AbstractRecordsTable createRecordsTable(PagePool pool, @NotNull Path recordsFile) throws IOException {
          return FSRecords.inlineAttributes && FSRecords.useSmallAttrTable
                 ? new CompactRecordsTable(recordsFile, pool, false)
                 : super.createRecordsTable(pool, recordsFile);
        }
      };

      contents = new RefCountingContentStorage(contentsFile,
                                               CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
                                               SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Content Write Pool"),
                                               FSRecords.useCompressionUtil,
                                               useContentHashes);

      // sources usually zipped with 4x ratio
      contentHashesEnumerator = useContentHashes ? new ContentHashEnumerator(contentsHashesFile, storageLockContext) : null;
      if (contentHashesEnumerator != null) {
        checkContentSanity(contents, contentHashesEnumerator);
      }

      SimpleStringPersistentEnumerator enumeratedAttributes = new SimpleStringPersistentEnumerator(enumeratedAttributesFile);

      boolean aligned = PagedFileStorage.BUFFER_SIZE % PersistentFSRecordsStorage.RECORD_SIZE == 0;
      if (!aligned) {
        LOG.error("Buffer size " + PagedFileStorage.BUFFER_SIZE + " is not aligned for record size " + PersistentFSRecordsStorage.RECORD_SIZE);
      }
      records = new PersistentFSRecordsStorage(new ResizeableMappedFile(recordsFile,
                                                                        20 * 1024,
                                                                        storageLockContext,
                                                                        PagedFileStorage.BUFFER_SIZE,
                                                                        aligned,
                                                                        IOUtil.useNativeByteOrderForByteBuffers()));

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
      IntList freeRecords = scanFreeRecords(records);
      return Pair.create(new PersistentFSConnection(persistentFSPaths,
                                                    records,
                                                    names,
                                                    attributes,
                                                    contents,
                                                    contentHashesEnumerator,
                                                    enumeratedAttributes,
                                                    freeRecords,
                                                    localModificationCounter,
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

        if (!deleted) {
          throw new IOException("Cannot delete filesystem storage files");
        }
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

  private static void invalidateIndex(@NotNull String reason) {
    LOG.info("Marking VFS as corrupted: " + reason);
    Path indexRoot = PathManager.getIndexRoot();
    if (Files.exists(indexRoot)) {
      String[] children = indexRoot.toFile().list();
      if (children != null && children.length > 0) {
        // create index corruption marker only if index directory exists and is non-empty
        // It is incorrect to consider non-existing indices "corrupted"
        FileUtil.createIfDoesntExist(PathManager.getIndexRoot().resolve("corruption.marker").toFile());
      }
    }
  }

  private static IntList scanFreeRecords(PersistentFSRecordsStorage records) throws IOException {
    final IntList freeRecords = new IntArrayList();
    final int fileLength = (int)records.length();
    LOG.assertTrue(fileLength % PersistentFSRecordsStorage.RECORD_SIZE == 0, "invalid file size: " + fileLength);

    int count = fileLength / PersistentFSRecordsStorage.RECORD_SIZE;
    for (int n = 2; n < count; n++) {
      if (BitUtil.isSet(records.doGetFlags(n), PersistentFSRecordAccessor.FREE_RECORD_FLAG)) {
        freeRecords.add(n);
      }
    }
    return freeRecords;
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
