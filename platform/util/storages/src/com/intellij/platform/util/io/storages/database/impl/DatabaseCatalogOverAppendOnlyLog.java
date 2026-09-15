// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.openapi.util.Ref;
import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkChangePayloadLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.CatalogChangeHeaderLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.CatalogChangeHeaderLayout.CatalogChangeRecordHeader;
import com.intellij.platform.util.io.storages.database.impl.layout.CatalogChangeHeaderLayout.ChangeType;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkChangePayloadLayout.ChunkChangeRecordPayload;
import com.intellij.platform.util.io.storages.database.impl.layout.DatabaseHeaderLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.StoreCreatePayloadLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.StoreCreatePayloadLayout.StoreCreateRecordPayload;
import com.intellij.platform.util.io.storages.database.impl.layout.StoreDropPayloadLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.StoreDropPayloadLayout.StoreDropRecordPayload;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;

/// Database catalog stored in an [AppendOnlyLogOverMMappedFile]
///
/// Naturally, [DatabaseHeaderLayout] should be located at the file start, but since we re-use [AppendOnlyLogOverMMappedFile]
/// for [DatabaseCatalog], some fields are duplicated: i.e. [DatabaseHeaderLayout#MAGIC] and [DatabaseHeaderLayout#FORMAT_MAJOR]
///  are stored in AOLog header, AND repeated in the first AOLog record -- so the first record tries to emulate 'standalone-file'
/// [DatabaseHeaderLayout.DatabaseHeader], even though right now it is not standalone.
///
///  So, the file layout of this [DatabaseCatalog] implementation goes like this:
/// ```
///  AppendOnlyLog.header {
///     MAGIC:               DatabaseHeaderLayout.MAGIC
///     <...other AOLog header fields...>
///     DATA_FORMAT_VERSION: DatabaseHeaderLayout.FORMAT_MAJOR
///  }
///  record[1] = DatabaseHeaderLayout {
///     MAGIC:        DatabaseHeaderLayout.MAGIC
///     FORMAT_MAJOR: DatabaseHeaderLayout.FORMAT_MAJOR
///     <...etc...>
///  }
///  record[>=1] = CatalogChangeRecordHeader { type: Something, version: ... } [Something]Payload { ... }
/// ```
final class DatabaseCatalogOverAppendOnlyLog implements DatabaseCatalog {

  private final @NotNull Path storagePath;

  private final @NotNull DatabaseHeaderLayout.DatabaseHeader header;

  private final @NotNull AppendOnlyLogOverMMappedFile catalogChangesLog;
  /** Data accumulated over {@link #catalogChangesLog} */
  private final @NotNull InMemoryCatalog currentCatalog;

  private boolean closed;

  private DatabaseCatalogOverAppendOnlyLog(@NotNull Path storagePath,
                                           @NotNull AppendOnlyLogOverMMappedFile catalogChangesLog,
                                           @NotNull DatabaseHeaderLayout.DatabaseHeader header,
                                           @NotNull InMemoryCatalog currentCatalog) {
    this.storagePath = storagePath;
    this.catalogChangesLog = catalogChangesLog;
    this.header = header;
    this.currentCatalog = currentCatalog;
  }

  /** Opens existing metadata or appends the first database header. */
  static @NotNull DatabaseCatalog open(@NotNull Path storagePath, int chunkSize) throws IOException {
    return open(storagePath, chunkSize, true);
  }

  static @NotNull DatabaseCatalog open(@NotNull Path storagePath, int chunkSize, boolean fsyncOnFlush) throws IOException {
    var logFactory = AppendOnlyLogFactory
      .withDefaults()
      .fsyncOnFlush(fsyncOnFlush)
      .magicWord(DatabaseHeaderLayout.MAGIC)
      .failIfFileIncompatible()
      .failIfDataFormatVersionNotMatch(DatabaseHeaderLayout.FORMAT_MAJOR);

    return logFactory.wrapStorageSafely(storagePath, log -> ensureDBHeaderInitialized(storagePath, log, chunkSize));
  }

  private static @NotNull DatabaseCatalog ensureDBHeaderInitialized(@NotNull Path storagePath,
                                                                    @NotNull AppendOnlyLogOverMMappedFile appendOnlyLog,
                                                                    int expectedChunkSize) throws IOException {
    LoadedCatalog loadedCatalog;
    if (appendOnlyLog.isEmpty()) {
      long databaseId = generateDatabaseID();

      var header = new DatabaseHeaderLayout.DatabaseHeader(databaseId, expectedChunkSize);
      appendOnlyLog.append(header::writeTo, DatabaseHeaderLayout.HEADER_SIZE);
      appendOnlyLog.flush(true);
      loadedCatalog = new LoadedCatalog(header, new InMemoryCatalog());
    }
    else {
      loadedCatalog = readCatalog(storagePath, appendOnlyLog);
      if (loadedCatalog.header().chunkSize() != expectedChunkSize) {
        throw new IOException(
          "[" + storagePath + "]: chunkSize(=" + loadedCatalog.header().chunkSize() +
          ") != expectedChunkSize(=" + expectedChunkSize + ")"
        );
      }
    }

    // TODO RC: Implement ownerPid, ownerStartedAt, and ownershipAcquiredAt -- to protect from concurrent process access.
    return new DatabaseCatalogOverAppendOnlyLog(storagePath, appendOnlyLog, loadedCatalog.header(), loadedCatalog.catalog());
  }

  private static long generateDatabaseID() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    while (true) {
      long databaseId = rnd.nextLong();
      if (databaseId != 0) {
        return databaseId;
      }
    }
  }

  /** Replays the database header and all later catalog changes. */
  private static @NotNull LoadedCatalog readCatalog(@NotNull Path storagePath,
                                                    @NotNull AppendOnlyLogOverMMappedFile appendOnlyLog) throws IOException {
    var headerRef = new Ref<DatabaseHeaderLayout.DatabaseHeader>();
    var catalog = new InMemoryCatalog();
    appendOnlyLog.forEachRecord((recordId, buffer) -> {
      if (headerRef.isNull()) {
        headerRef.set(DatabaseHeaderLayout.DatabaseHeader.read(storagePath, buffer));
      }
      else {
        readCatalogRecord(storagePath, recordId, buffer, catalog);
      }
      return true;
    });

    var header = headerRef.get();
    if (header == null) {
      throw new CorruptedException("[" + storagePath + "]: database.meta has no Database header record");
    }
    return new LoadedCatalog(header, catalog);
  }

  private static void readCatalogRecord(@NotNull Path storagePath,
                                        long recordId,
                                        @NotNull ByteBuffer source,
                                        @NotNull InMemoryCatalog catalog) throws IOException {
    if (source.remaining() < CatalogChangeHeaderLayout.HEADER_SIZE) {
      throw corruptedCatalogRecord(storagePath, recordId, "the record header is truncated", null);
    }

    var recordHeader = CatalogChangeRecordHeader.read(source);
    var recordType = recordHeader.type();
    var payload = source.slice(
      CatalogChangeHeaderLayout.HEADER_SIZE,
      source.remaining() - CatalogChangeHeaderLayout.HEADER_SIZE
    );

    try {
      switch (recordType) {
        case CHUNK_CREATE -> catalog.addChunk(readChunkChangeRecord(storagePath, recordId, recordHeader.version(), payload).chunkId());
        case CHUNK_SEAL -> catalog.sealChunk(readChunkChangeRecord(storagePath, recordId, recordHeader.version(), payload).chunkId());
        case CHUNK_RETIRE -> catalog.retireChunk(readChunkChangeRecord(storagePath, recordId, recordHeader.version(), payload).chunkId());
        case STORE_CREATE -> {
          var store = readStoreCreateRecord(storagePath, recordId, recordHeader.version(), payload);
          catalog.addStore(store.storeId(), store.name(), store.dataVersion());
        }
        case STORE_DROP -> catalog.dropStore(readStoreDropRecord(storagePath, recordId, recordHeader.version(), payload).storeId());
      }
    }
    catch (IllegalArgumentException | IllegalStateException e) {
      throw corruptedCatalogRecord(storagePath, recordId, e.getMessage(), e);
    }
  }

  private static @NotNull ChunkChangeRecordPayload readChunkChangeRecord(@NotNull Path storagePath,
                                                                         long recordId,
                                                                         short recordVersion,
                                                                         @NotNull ByteBuffer source) throws IOException {
    if (recordVersion != ChunkChangePayloadLayout.PAYLOAD_FORMAT_VERSION) {
      throw new UnsupportedFormatException(
        "chunk change record " + recordId + " in " + storagePath,
        Short.toUnsignedInt(ChunkChangePayloadLayout.PAYLOAD_FORMAT_VERSION),
        Short.toUnsignedInt(recordVersion)
      );
    }
    if (source.remaining() != ChunkChangePayloadLayout.PAYLOAD_SIZE) {
      throw corruptedCatalogRecord(
        storagePath,
        recordId,
        "payload size is " + source.remaining() + ", expected " + ChunkChangePayloadLayout.PAYLOAD_SIZE,
        null
      );
    }
    return ChunkChangeRecordPayload.read(source);
  }

  private static @NotNull StoreCreateRecordPayload readStoreCreateRecord(@NotNull Path storagePath,
                                                                         long recordId,
                                                                         short recordVersion,
                                                                         @NotNull ByteBuffer source) throws IOException {
    if (recordVersion != StoreCreatePayloadLayout.PAYLOAD_FORMAT_VERSION) {
      throw new UnsupportedFormatException(
        "store creation record " + recordId + " in " + storagePath,
        Short.toUnsignedInt(StoreCreatePayloadLayout.PAYLOAD_FORMAT_VERSION),
        Short.toUnsignedInt(recordVersion)
      );
    }
    try {
      return StoreCreateRecordPayload.read(source);
    }
    catch (CorruptedException e) {
      throw corruptedCatalogRecord(storagePath, recordId, e.getMessage(), e);
    }
  }

  private static @NotNull StoreDropRecordPayload readStoreDropRecord(@NotNull Path storagePath,
                                                                     long recordId,
                                                                     short recordVersion,
                                                                     @NotNull ByteBuffer source) throws IOException {
    if (recordVersion != StoreDropPayloadLayout.PAYLOAD_FORMAT_VERSION) {
      throw new UnsupportedFormatException(
        "store drop record " + recordId + " in " + storagePath,
        Short.toUnsignedInt(StoreDropPayloadLayout.PAYLOAD_FORMAT_VERSION),
        Short.toUnsignedInt(recordVersion)
      );
    }
    if (source.remaining() != StoreDropPayloadLayout.PAYLOAD_SIZE) {
      throw corruptedCatalogRecord(
        storagePath,
        recordId,
        "payload size is " + source.remaining() + ", expected " + StoreDropPayloadLayout.PAYLOAD_SIZE,
        null
      );
    }
    return StoreDropRecordPayload.read(source);
  }

  private static @NotNull CorruptedException corruptedCatalogRecord(@NotNull Path storagePath,
                                                                    long recordId,
                                                                    @NotNull String details,
                                                                    Throwable cause) {
    return new CorruptedException("[" + storagePath + "]: invalid catalog record " + recordId + ": " + details, cause);
  }

  @Override
  public synchronized long databaseId() {
    return header.databaseId();
  }

  @Override
  public synchronized int chunkSize() {
    return header.chunkSize();
  }

  @Override
  public synchronized @NotNull List<StoreInfo> stores() {
    return currentCatalog.stores();
  }

  @Override
  public synchronized @Nullable DatabaseCatalog.StoreInfo findStore(@NotNull String name) {
    return currentCatalog.findStore(name);
  }

  @Override
  public synchronized int nextStoreId() {
    return currentCatalog.nextStoreId();
  }

  @Override
  public synchronized void registerNewStore(int storeId, @NotNull String name, int dataVersion) throws IOException {
    ensureOpen();
    var expectedStoreId = currentCatalog.nextStoreId();
    if (storeId != expectedStoreId) {
      throw new IllegalArgumentException("storeId(=" + storeId + ") must be the next storeId(=" + expectedStoreId + ")");
    }
    currentCatalog.validateStoreCreation(storeId, name);

    var header = new CatalogChangeRecordHeader(ChangeType.STORE_CREATE, StoreCreatePayloadLayout.PAYLOAD_FORMAT_VERSION);
    var payload = new StoreCreateRecordPayload(storeId, dataVersion, name);
    catalogChangesLog.append(
      buffer -> {
        header.writeTo(buffer);
        payload.writeTo(buffer);
        return buffer;
      },
      Math.addExact(CatalogChangeHeaderLayout.HEADER_SIZE, payload.payloadSize())
    );
    currentCatalog.addStore(storeId, name, dataVersion);
  }

  @Override
  public synchronized void dropStore(int storeId) throws IOException {
    ensureOpen();
    currentCatalog.validateStoreDrop(storeId);

    var header = new CatalogChangeRecordHeader(ChangeType.STORE_DROP, StoreDropPayloadLayout.PAYLOAD_FORMAT_VERSION);
    var payload = new StoreDropRecordPayload(storeId);
    catalogChangesLog.append(
      buffer -> {
        header.writeTo(buffer);
        payload.writeTo(buffer);
        return buffer;
      },
      CatalogChangeHeaderLayout.HEADER_SIZE + StoreDropPayloadLayout.PAYLOAD_SIZE
    );
    currentCatalog.dropStore(storeId);
  }

  @Override
  public synchronized @NotNull List<ChunkInfo> chunks() {
    return currentCatalog.chunks();
  }

  @Override
  public synchronized @Nullable ChunkInfo findChunk(int chunkId) {
    return currentCatalog.findChunk(chunkId);
  }

  @Override
  public synchronized int nextChunkId() {
    return currentCatalog.nextChunkId();
  }

  @Override
  public synchronized void registerNewChunk(int chunkId) throws IOException {
    ensureOpen();
    var expectedChunkId = currentCatalog.nextChunkId();
    if (chunkId != expectedChunkId) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") must be the next chunkId(=" + expectedChunkId + ")");
    }

    appendChunkChangeRecord(chunkId, ChangeType.CHUNK_CREATE);
    currentCatalog.addChunk(chunkId);
  }

  /** Appends the state change before publishing the sealed state. */
  @Override
  public synchronized void markChunkSealed(int chunkId) throws IOException {
    appendChunkStateChange(chunkId, ACTIVE, ChangeType.CHUNK_SEAL);
    currentCatalog.sealChunk(chunkId);
  }

  /** Appends the state change before publishing the retired state. */
  @Override
  public synchronized void markChunkRetired(int chunkId) throws IOException {
    appendChunkStateChange(chunkId, SEALED, ChangeType.CHUNK_RETIRE);
    currentCatalog.retireChunk(chunkId);
  }

  private void appendChunkStateChange(int chunkId,
                                      @NotNull ChunkState expectedState,
                                      @NotNull ChangeType changeType) throws IOException {
    ensureOpen();
    currentCatalog.validateTransition(chunkId, expectedState);
    appendChunkChangeRecord(chunkId, changeType);
  }

  private void appendChunkChangeRecord(int chunkId,
                                       @NotNull ChangeType changeType) throws IOException {
    var header = new CatalogChangeRecordHeader(changeType, ChunkChangePayloadLayout.PAYLOAD_FORMAT_VERSION);
    var payload = new ChunkChangeRecordPayload(chunkId);
    catalogChangesLog.append(
      buffer -> {
        header.writeTo(buffer);
        payload.writeTo(buffer);
        return buffer;
      },
      CatalogChangeHeaderLayout.HEADER_SIZE + ChunkChangePayloadLayout.PAYLOAD_SIZE
    );
  }

  @Override
  public synchronized boolean isDirty() {
    return false;
  }

  @Override
  public synchronized void flush() throws IOException {
    ensureOpen();
    catalogChangesLog.flush();
  }

  @Override
  public synchronized void fsync() throws IOException {
    ensureOpen();
    catalogChangesLog.flush(true);
  }

  @Override
  public synchronized boolean isClosed() {
    return closed;
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }

    try {
      catalogChangesLog.closeAndUnsafelyUnmap();
    }
    finally {
      closed = true;
    }
  }

  private void ensureOpen() throws ClosedStorageException {
    if (closed) {
      throw new ClosedStorageException("Database metadata is already closed: " + storagePath);
    }
  }

  /** The metadata loaded from the changes log */
  private record LoadedCatalog(@NotNull DatabaseHeaderLayout.DatabaseHeader header, @NotNull InMemoryCatalog catalog) {
  }
}
