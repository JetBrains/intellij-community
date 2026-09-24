// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.RETIRED;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;

/** The set of chunks that belong to the database */
final class DatabaseChunks implements Closeable, Flushable {
  private static final Logger LOG = Logger.getInstance(DatabaseChunks.class);

  private static final int MAX_ACTIVE_CHUNKS = 2;

  //TODO RC: use MessageFormat?
  private static final String CHUNK_FILE_PREFIX = "chunk-";
  private static final String CHUNK_FILE_SUFFIX = ".dat";

  private final @NotNull Path databaseDirectory;
  private final @NotNull DatabaseCatalog databaseCatalog;

  private final boolean fsyncOnFlush;

  private final transient @NotNull Object lock = new Object();

  /// Chunks DB currently works with: both active and sealed;
  /// GuardedBy(lock)
  private final Int2ObjectSortedMap<DatabaseChunk> chunksById = new Int2ObjectLinkedOpenHashMap<>();

  /// All active chunks, oldest first.
  /// GuardedBy(lock)
  private final ArrayDeque<DatabaseChunk> activeChunks = new ArrayDeque<>();

  /// Retired chunks awaiting resource deallocation, by chunkId.
  /// These chunks have `state=RETIRED` and are removed from [#chunksById] -- i.e., the database API does not expose them.
  /// These chunks could be unmapped & removed ([#dropRetiredChunks()]) only outside normal DB operation, when none of the
  /// clients could possibly have a reference to their mmapped buffers. This is (currently) possible only on startup/shutdown.
  ///
  /// GuardedBy(lock)
  private final Int2ObjectMap<DatabaseChunk> chunksPendingForRelease = new Int2ObjectOpenHashMap<>();

  /// GuardedBy(lock)
  private boolean closed;

  /** Creates an empty owner before reconciliation can open mappings */
  private DatabaseChunks(@NotNull Path databaseDirectory, @NotNull DatabaseCatalog databaseCatalog, boolean fsyncOnFlush) {
    this.databaseDirectory = databaseDirectory;
    this.databaseCatalog = databaseCatalog;
    this.fsyncOnFlush = fsyncOnFlush;
  }

  /** Opens every registered non-retired chunk, then deletes unused chunk files */
  static @NotNull DatabaseChunks open(@NotNull Path databaseDirectory, @NotNull DatabaseCatalog databaseCatalog) throws IOException {
    return open(databaseDirectory, databaseCatalog, true);
  }

  static @NotNull DatabaseChunks open(@NotNull Path databaseDirectory,
                                      @NotNull DatabaseCatalog databaseCatalog,
                                      boolean fsyncOnFlush) throws IOException {
    Files.createDirectories(databaseDirectory);
    var chunks = new DatabaseChunks(databaseDirectory, databaseCatalog, fsyncOnFlush);
    try {
      chunks.openRegisteredChunks();
      //TODO RC: this method failures shouldn't prevent DB opening
      chunks.deleteOrphanChunkFiles();
      return chunks;
    }
    catch (IOException openingFailure) {
      try {
        chunks.close();
      }
      catch (IOException closeFailure) {
        openingFailure.addSuppressed(closeFailure);
      }
      throw openingFailure;
    }
  }

  /** Creates a new chunk; saves it & registers it in the catalog; */
  @NotNull DatabaseChunk createChunk() throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var chunkId = databaseCatalog.nextChunkId();
      var chunk = DatabaseChunk.create(
        chunkPath(databaseDirectory, chunkId),
        databaseCatalog.databaseId(),
        chunkId,
        databaseCatalog.chunkSize(),
        fsyncOnFlush
      );

      //TODO RC: extract common method like closeIfFailed(callable)
      try {
        databaseCatalog.registerNewChunk(chunkId);
      }
      catch (IOException registrationFailure) {
        try {
          chunk.close();
        }
        catch (IOException closeFailure) {
          registrationFailure.addSuppressed(closeFailure);
        }
        throw registrationFailure;
      }

      chunksById.put(chunkId, chunk);
      activeChunks.addLast(chunk);
      sealOldestActiveChunks();
      LOG.info("Created chunk #" + chunkId + ": " + chunk.mappedSize() + "b; " +
               chunksById.size() + " total chunks, [" + chunk.storagePath() + "]");
      return chunk;
    }
  }

  /** @return a snapshot of open (active & sealed) chunks */
  @NotNull List<DatabaseChunk> chunks() {
    synchronized (lock) {
      return List.copyOf(chunksById.values());
    }
  }

  /**
   * Returns an active chunk for the block.
   * Creating a chunk can seal the oldest active chunk.
   */
  @NotNull DatabaseChunk chunkForAllocation(int blockLength) throws IOException {
    if (blockLength <= 0) {
      throw new IllegalArgumentException("blockLength(=" + blockLength + ") must be positive");
    }
    synchronized (lock) {
      ensureNotClosed();
      for (var chunk : activeChunks) {
        ensureChunkStateIsConsistentWithCatalog(chunk);
        if (chunk.canAllocate(blockLength)) {
          return chunk;
        }
      }
      return createChunk();
    }
  }

  private void ensureChunkStateIsConsistentWithCatalog(@NotNull DatabaseChunk chunk) {
    //TODO RC: shouldn't we checked the consistency once, on DB startup recovery? If true then why re-check it every time?
    //Chunk state is duplicated: Catalog keeps it as a part of DB metadata -- while DatabaseChunk itself keeps it in its
    // header. Those states must be in sync, but they could diverge e.g. on crash: chunk header is changed first, DB catalog
    // after it, so there is a time-window for crash to leave chunk updated by DB catalog stale. So, lets re-check:
    DatabaseCatalog.ChunkState catalogChunkState = catalogChunkInfo(chunk.chunkId()).state();
    if (catalogChunkState != chunk.state()) {
      throw new IllegalStateException(
        chunk + ".state(=" + chunk.state() + ") is out of sync with catalog(=" + catalogChunkState + ")"
      );
    }
  }

  private void sealOldestActiveChunks() throws IOException {
    while (activeChunks.size() > MAX_ACTIVE_CHUNKS) {
      // Keep invariant: always[active chunks' chunkId > sealed chunks' chunkId]
      // The invariant is relied upon in block evacuation: we demand that block is always evacuated into a newer (by chunkId) chunk
      var oldest = activeChunks.getFirst();
      ensureChunkStateIsConsistentWithCatalog(oldest);
      sealForAllocation(oldest);
      activeChunks.removeFirstOccurrence(oldest);
    }
  }

  @NotNull List<DatabaseChunk> sealedChunks() {
    synchronized (lock) {
      return chunksById.values().stream()
        .filter(chunk -> chunk.state() == SEALED)
        .sorted(Comparator.comparingInt(DatabaseChunk::chunkId))
        .toList();
    }
  }

  /** Completes the file-first seal transition before removing a chunk from the active queue */
  private void sealForAllocation(@NotNull DatabaseChunk chunk) throws IOException {
    if (chunk.state() != ACTIVE) {
      throw new IllegalStateException("Chunk " + chunk.chunkId() + " state is " + chunk.state() + ", catalog state is " + ACTIVE);
    }

    chunk.seal();
    databaseCatalog.markChunkSealed(chunk.chunkId());
    databaseCatalog.flush();
  }

  private @NotNull DatabaseCatalog.ChunkInfo catalogChunkInfo(int chunkId) {
    var chunkInfo = databaseCatalog.findChunk(chunkId);
    if (chunkInfo == null) {
      throw new IllegalStateException("Unknown chunkId(=" + chunkId + ")");
    }
    return chunkInfo;
  }

  /// Makes chunk (which should be already retired) unavailable for outside observers, and puts it into queue for resource releasing
  void postponeForRelease(@NotNull DatabaseChunk chunkToRelease) {
    synchronized (lock) {
      if (!chunksById.remove(chunkToRelease.chunkId(), chunkToRelease)) {
        throw new IllegalArgumentException("Unknown chunkId(=" + chunkToRelease.chunkId() + ")");
      }
      chunksPendingForRelease.put(chunkToRelease.chunkId(), chunkToRelease);
    }
  }

  int chunkSize() {
    return databaseCatalog.chunkSize();
  }

  private void openRegisteredChunks() throws IOException {
    synchronized (lock) {
      for (var chunkInfo : databaseCatalog.chunks()) {
        if (chunkInfo.state() == RETIRED) {
          continue;//do not open (mmap) retired chunks
        }
        var chunkPath = chunkPath(databaseDirectory, chunkInfo.chunkId());
        if (!Files.exists(chunkPath)) {
          throw new CorruptedException("[" + chunkPath + "]: the catalog references a missing chunk file");
        }

        var chunk = DatabaseChunk.open(
          chunkPath,
          databaseCatalog.databaseId(),
          chunkInfo.chunkId(),
          databaseCatalog.chunkSize(),
          fsyncOnFlush
        );
        chunksById.put(chunkInfo.chunkId(), chunk);

        //We do a (limited) recovery/reconciliation here, on DB opening -- but afterward, during normal DB operation,
        // we assume that no inconsistency could happen. Hence, we do not look for recovery/reconciliation anywhere
        // else, but on DB opening. This is intentional design choice that simplifies the code.
        reconcileChunkState(chunkInfo, chunk);
        if (chunk.state() == RETIRED) {
          chunk.close();
          chunksById.remove(chunkInfo.chunkId(), chunk);
        }
        else {
          if (chunk.state() == ACTIVE) {
            activeChunks.addLast(chunk);
          }
          LOG.info("Opened chunk " + chunkInfo.chunkId() + " (" + chunk.state() + "): " + chunkPath);
        }
      }
      if (activeChunks.size() > MAX_ACTIVE_CHUNKS) {
        var activeCount = activeChunks.size();
        sealOldestActiveChunks();
        LOG.info("Opened database with " + activeCount + " active chunks; sealed " +
                 (activeCount - activeChunks.size()) + " oldest chunks to keep at most " + MAX_ACTIVE_CHUNKS);
      }
    }
  }

  /// Completes state transition after an interrupted DB operation: the chunk header is changed first, then catalog entry,
  /// hence it could be `chunk.state() > DatabaseCatalog.chunkState` => make DB catalog catch up
  private void reconcileChunkState(@NotNull DatabaseCatalog.ChunkInfo chunkInfo,
                                   @NotNull DatabaseChunk chunk) throws IOException {
    var headerState = chunk.state();
    if (headerState == chunkInfo.state()) {
      return;
    }
    if (chunkInfo.state() == ACTIVE && headerState == SEALED) {
      databaseCatalog.markChunkSealed(chunkInfo.chunkId());
    }
    else if (chunkInfo.state() == SEALED && headerState == RETIRED) {
      databaseCatalog.markChunkRetired(chunkInfo.chunkId());
    }
    else {
      throw new CorruptedException(
        "[" + chunk.storagePath() + "]: chunk state is " + headerState + ", catalog state is " + chunkInfo.state()
      );
    }
    databaseCatalog.flush();
    LOG.info("Recovered chunk " + chunkInfo.chunkId() + " state " + headerState + ": " + chunk.storagePath());
  }

  /// Removes files that have chunk-like names, but are not registered in the catalog
  /// (likely remnants of unfinished previous cleanups)
  private void deleteOrphanChunkFiles() throws IOException {
    synchronized (lock) {
      var registeredChunkIds = new IntOpenHashSet();
      for (var chunkInfo : databaseCatalog.chunks()) {
        registeredChunkIds.add(chunkInfo.chunkId());
      }

      try (DirectoryStream<Path> files = Files.newDirectoryStream(databaseDirectory)) {
        for (var file : files) {
          var chunkId = parseChunkId(file.getFileName().toString());
          if (chunkId > 0 &&
              !registeredChunkIds.contains(chunkId) &&
              Files.isRegularFile(file)) {
            if (Files.deleteIfExists(file)) {
              LOG.info("Deleted unregistered chunk " + chunkId + ": " + file);
            }
          }
        }
      }
    }
  }

  /// Deletes all retired chunk files;
  /// Retired chunks that are already opened -- are closed (and unmapped) beforehand.
  ///
  /// This method must be called _only_ when there is no chance chunks [#chunksPendingForRelease] could possibly
  /// be referenced by the clients: since the chunks are unmapped during the method -- all derived memory segments
  /// become invalid without notification.
  void dropRetiredChunks() throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      for (var chunkInfo : databaseCatalog.chunks()) {
        if (chunkInfo.state() == RETIRED) {
          var chunkId = chunkInfo.chunkId();
          var mappedChunk = chunksPendingForRelease.get(chunkId);
          if (mappedChunk != null) {
            mappedChunk.close();
            chunksPendingForRelease.remove(chunkId, mappedChunk);
          }

          var file = chunkPath(databaseDirectory, chunkId);
          if (Files.deleteIfExists(file)) {
            LOG.info("Deleted retired chunk " + chunkId + ": " + file);
          }
        }
      }
    }
  }

  /** @return the canonical file path for a chunkId */
  static @NotNull Path chunkPath(@NotNull Path databaseDirectory, int chunkId) {
    if (chunkId <= 0) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") must be positive");
    }
    String chunkFileName = String.format(Locale.ROOT, CHUNK_FILE_PREFIX + "%08d" + CHUNK_FILE_SUFFIX, chunkId);
    return databaseDirectory.resolve(chunkFileName);
  }

  /**
   * Tries to parse a file name as a chunk name
   *
   * @return chunkId, if parsing is successful, -1 if fileName is not chunk name
   */
  private static int parseChunkId(@NotNull String fileName) {
    if (!fileName.startsWith(CHUNK_FILE_PREFIX) || !fileName.endsWith(CHUNK_FILE_SUFFIX)) {
      return -1;
    }

    var numericPart = fileName.substring(CHUNK_FILE_PREFIX.length(), fileName.length() - CHUNK_FILE_SUFFIX.length());
    try {
      var chunkId = Integer.parseInt(numericPart);
      if (chunkId <= 0) {
        return -1;
      }
      return fileName.equals(chunkPath(Path.of(""), chunkId).toString()) ? chunkId : -1;
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  /** Flushes every open chunk before the catalog is flushed */
  @Override
  public void flush() throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      for (var chunk : chunksById.values()) {
        chunk.flush();
      }
    }
  }

  /// Only for use from [BlocksDatabaseImpl]
  void fsync() throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      for (var chunk : chunksById.values()) {
        chunk.fsync();
      }
    }
  }

  boolean isClosed() {
    synchronized (lock) {
      return closed;
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (lock) {
      if (closed) {
        return;
      }
      try {
        var chunks = new DatabaseChunk[chunksById.size() + chunksPendingForRelease.size()];
        var index = 0;
        for (var chunk : chunksById.values()) {
          chunks[index++] = chunk;
        }
        for (var chunk : chunksPendingForRelease.values()) {
          chunks[index++] = chunk;
        }
        IOUtil.closeAllSafely(chunks);
      }
      finally {
        chunksById.clear();
        chunksPendingForRelease.clear();
        closed = true;
      }
    }
  }

  private void ensureNotClosed() throws IOException {
    if (closed) {
      throw new IOException("Database chunks are already closed: " + databaseDirectory);
    }
  }
}
