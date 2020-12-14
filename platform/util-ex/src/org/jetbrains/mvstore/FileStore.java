/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default storage mechanism of the MVStore. This implementation persists
 * data to a file. The file store is responsible to persist data and for free
 * space management.
 */
final class FileStore {
    static final Set<? extends OpenOption> R = Collections.singleton(StandardOpenOption.READ);
    static final Set<? extends OpenOption> RW = Collections.unmodifiableSet(EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    static final Set<? extends OpenOption> RW_TRUNCATE = Collections.unmodifiableSet(EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

    /**
     * The number of read operations.
     */
    private final AtomicLong readCount = new AtomicLong();

    /**
     * The number of write operations.
     */
    private final AtomicLong writeCount = new AtomicLong();

    /**
     * The number of written bytes.
     */
    private final AtomicLong writeBytes = new AtomicLong();

    /**
     * The free spaces between the chunks. The first block to use is block 2
     * (the first two blocks are the store header).
     */
    final FreeSpaceBitSet freeSpace = new FreeSpaceBitSet(2, MVStore.BLOCK_SIZE);

    /**
     * The file name.
     */
    private final Path file;

    /**
     * Whether this store is read-only.
     */
    private final boolean readOnly;

    /**
     * The file size (cached).
     */
    private long fileSize;

    /**
     * The file.
     */
    private final FileChannel fileChannel;

    /**
     * The file lock.
     */
    private FileLock fileLock;

    FileStore(@NotNull Path file, Set<? extends OpenOption> options, @SuppressWarnings("unused") boolean useFileCache) throws IOException {
        this.file = file;
        readOnly = !options.contains(StandardOpenOption.WRITE);
        if (options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW)) {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
        fileChannel = FileChannel.open(file, options);
        try {
            try {
                if (readOnly) {
                    fileLock = fileChannel.tryLock(0, Long.MAX_VALUE, true);
                } else {
                    fileLock = fileChannel.tryLock();
                }
            } catch (NonWritableChannelException e) {
                if (readOnly && !Files.exists(file)) {
                    throw new MVStoreException(MVStoreException.ERROR_READING_FAILED, "The file not found: " + this.file, e);
                } else {
                    throw e;
                }
            } catch (OverlappingFileLockException e) {
                throw new MVStoreException(MVStoreException.ERROR_FILE_LOCKED, "The file is locked: " + this.file, e);
            }
            if (fileLock == null) {
                try { close(); } catch (Exception ignore) {}
                throw new MVStoreException(MVStoreException.ERROR_FILE_LOCKED, "The file is locked: " + this.file);
            }
            fileSize = fileChannel.size();
        } catch (IOException e) {
            try { close(); } catch (Exception ignore) {}
            throw e;
        }
    }

    @Override
    public final String toString() {
        return file.toString();
    }

    public void readFully(ByteBuf out, long position, int length) {
        DataUtil.readFully(fileChannel, position, length, out);
        readCount.incrementAndGet();
    }

    public void copy(long from, long to, int length) {
        try (FileChannel sourceFileChannel = FileChannel.open(file, R)) {
            sourceFileChannel.position(from);
            fileChannel.transferFrom(sourceFileChannel, to, length);
        }
        catch (IOException e) {
            throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED, "Closing failed for file: " + file, e);
        }
        readCount.incrementAndGet();
    }

    /**
     * Write to the file.
     *
     * @param in the source buffer
     * @param position the write position
     */
    public void writeFully(ByteBuf in, long position) {
        int length = in.readableBytes();
        fileSize = Math.max(fileSize, position + length);
        DataUtil.writeFully(fileChannel, position, in);
        writeCount.incrementAndGet();
        writeBytes.addAndGet(length);
    }

    /**
     * Close this store.
     */
    public void close() {
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                if (fileLock != null) {
                    fileLock.release();
                }
                fileChannel.close();
            }
        } catch (Exception e) {
            throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED, "Closing failed for file: " + file, e);
        } finally {
            fileLock = null;
        }
    }

    /**
     * Flush all changes.
     */
    final void sync() {
        if (fileChannel != null) {
            try {
                fileChannel.force(true);
            } catch (IOException e) {
                throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED, "Could not sync file " + file);
            }
        }
    }

    /**
     * Get the file size.
     *
     * @return the file size
     */
    final long size() {
        return fileSize;
    }

    /**
     * Truncate the file.
     *
     * @param size the new file size
     */
    public void truncate(long size) {
        int attemptCount = 0;
        while (true) {
            try {
                writeCount.incrementAndGet();
                fileChannel.truncate(size);
                 fileSize = Math.min(fileSize, size);
                return;
            } catch (IOException e) {
                if (++attemptCount == 10) {
                    throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED,
                                               "Could not truncate file " + file + " to size " + size, e);
                }
                Thread.yield();
            }
        }
    }

    /**
     * Get the number of write operations since this store was opened.
     * For file based stores, this is the number of file write operations.
     *
     * @return the number of write operations
     */
    final long getWriteCount() {
        return writeCount.get();
    }

    ///**
    // * Get the number of written bytes since this store was opened.
    // *
    // * @return the number of write operations
    // */
    //final long getWriteBytes() {
    //    return writeBytes.get();
    //}

    /**
     * Get the number of read operations since this store was opened.
     * For file based stores, this is the number of file read operations.
     *
     * @return the number of read operations
     */
    final long getReadCount() {
        return readCount.get();
    }

    final boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Get the default retention time for this store in milliseconds.
     */
    @SuppressWarnings("MethodMayBeStatic")
    final int getDefaultRetentionTime() {
        return 45_000;
    }

    /**
     * Mark the space as in use.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     */
    final void markUsed(long pos, int length) {
        freeSpace.markUsed(pos, length);
    }

    /**
     * Allocate a number of blocks and mark them as used.
     *
     * @param length the number of bytes to allocate
     * @param reservedLow start block index of the reserved area (inclusive)
     * @param reservedHigh end block index of the reserved area (exclusive),
     *                     special value -1 means beginning of the infinite free area
     * @return the start position in bytes
     */
    final long allocate(int length, long reservedLow, long reservedHigh) {
        return freeSpace.allocate(length, reservedLow, reservedHigh);
    }

    /**
     * Calculate starting position of the prospective allocation.
     *
     * @param blocks the number of blocks to allocate
     * @param reservedLow start block index of the reserved area (inclusive)
     * @param reservedHigh end block index of the reserved area (exclusive),
     *                     special value -1 means beginning of the infinite free area
     * @return the starting block index
     */
    final long predictAllocation(int blocks, long reservedLow, long reservedHigh) {
        return freeSpace.predictAllocation(blocks, reservedLow, reservedHigh);
    }

    /**
     * Mark the space as free.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     */
    final void free(long pos, int length) {
        freeSpace.free(pos, length);
    }

    final int getFillRate() {
        return freeSpace.getFillRate();
    }

    /**
     * Calculates a prospective fill rate, which store would have after rewrite
     * of sparsely populated chunk(s) and evacuation of still live data into a
     * new chunk.
     *
     * @param vacatedBlocks
     *            number of blocks vacated
     * @return prospective fill rate (0 - 100)
     */
    final int getProjectedFillRate(int vacatedBlocks) {
        return freeSpace.getProjectedFillRate(vacatedBlocks);
    }

    final long getFirstFree() {
        return freeSpace.getFirstFree();
    }

    final long getFileLengthInUse() {
        return freeSpace.getLastFree();
    }

    /**
     * Calculates relative "priority" for chunk to be moved.
     *
     * @param block where chunk starts
     * @return priority, bigger number indicate that chunk need to be moved sooner
     */
    final int getMovePriority(int block) {
        return freeSpace.getMovePriority(block);
    }

    final long getAfterLastBlock() {
        return freeSpace.getAfterLastBlock();
    }

    /**
     * Mark the file as empty.
     */
    public void clear() {
        freeSpace.clear();
    }
}
