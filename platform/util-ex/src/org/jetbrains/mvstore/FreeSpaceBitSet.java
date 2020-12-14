/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import java.util.BitSet;

/**
 * A free space bit set.
 */
final class FreeSpaceBitSet {
    private static final boolean DETAILED_INFO = false;

    /**
     * The first usable block.
     */
    private final int firstFreeBlock;

    /**
     * The block size in bytes.
     */
    private final int blockSize;

    /**
     * The bit set.
     */
    private final BitSet set = new BitSet();

    /**
     * Left-shifting register, which holds outcomes of recent allocations. Only
     * allocations done in "reuseSpace" mode are recorded here. For example,
     * rightmost bit set to 1 means that last allocation failed to find a hole
     * big enough, and next bit set to 0 means that previous allocation request
     * have found one.
     */
    private int failureFlags;


    /**
     * Create a new free space map.
     *
     * @param firstFreeBlock the first free block
     * @param blockSize the block size
     */
    FreeSpaceBitSet(int firstFreeBlock, int blockSize) {
        this.firstFreeBlock = firstFreeBlock;
        this.blockSize = blockSize;
        clear();
    }

    /**
     * Reset the list.
     */
    public void clear() {
        set.clear();
        set.set(0, firstFreeBlock);
    }

    /**
     * Check whether one of the blocks is in use.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     * @return true if a block is in use
     */
    public boolean isUsed(long pos, int length) {
        int start = getBlock(pos);
        int blocks = getBlockCount(length);
        for (int i = start; i < start + blocks; i++) {
            if (!set.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether one of the blocks is free.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     * @return true if a block is free
     */
    public boolean isFree(long pos, int length) {
        int start = getBlock(pos);
        int blocks = getBlockCount(length);
        for (int i = start; i < start + blocks; i++) {
            if (set.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allocate a number of blocks and mark them as used.
     *
     * @param length the number of bytes to allocate
     * @return the start position in bytes
     */
    public long allocate(int length) {
        return allocate(length, 0, 0);
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
    long allocate(int length, long reservedLow, long reservedHigh) {
        return getPos(allocate(getBlockCount(length), (int)reservedLow, (int)reservedHigh, true));
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
    long predictAllocation(int blocks, long reservedLow, long reservedHigh) {
        return allocate(blocks, (int)reservedLow, (int)reservedHigh, false);
    }

    boolean isFragmented() {
        return Integer.bitCount(failureFlags & 0x0F) > 1;
    }

    private int allocate(int blocks, int reservedLow, int reservedHigh, boolean allocate) {
        int freeBlocksTotal = 0;
        for (int i = 0;;) {
            int start = set.nextClearBit(i);
            int end = set.nextSetBit(start + 1);
            int freeBlocks = end - start;
            if (end < 0 || freeBlocks >= blocks) {
                if ((reservedHigh < 0 || start < reservedHigh) && start + blocks > reservedLow) { // overlap detected
                    if (reservedHigh < 0) {
                        start = getAfterLastBlock();
                        end = -1;
                    } else {
                        i = reservedHigh;
                        continue;
                    }
                }
                assert set.nextSetBit(start) == -1 || set.nextSetBit(start) >= start + blocks :
                        "Double alloc: " + Integer.toHexString(start) + "/" + Integer.toHexString(blocks) + " " + this;
                if (allocate) {
                    set.set(start, start + blocks);
                } else {
                    failureFlags <<= 1;
                    if (end < 0 && freeBlocksTotal > 4 * blocks) {
                        failureFlags |= 1;
                    }
                }
                return start;
            }
            freeBlocksTotal += freeBlocks;
            i = end;
        }
    }

    /**
     * Mark the space as in use.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     */
    public void markUsed(long pos, int length) {
        int start = getBlock(pos);
        int blocks = getBlockCount(length);
        // this is not an assert because we get called during file opening
        if (set.nextSetBit(start) != -1 && set.nextSetBit(start) < start + blocks ) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "Double mark: " + Integer.toHexString(start) + "/" + Integer.toHexString(blocks) + " " + this);
        }
        set.set(start, start + blocks);
    }

    /**
     * Mark the space as free.
     *
     * @param pos the position in bytes
     * @param length the number of bytes
     */
    public void free(long pos, int length) {
        int start = getBlock(pos);
        int blocks = getBlockCount(length);
        assert set.nextClearBit(start) >= start + blocks :
                "Double free: " + Integer.toHexString(start) + "/" + Integer.toHexString(blocks) + " " + this;
        set.clear(start, start + blocks);
    }

    private long getPos(int block) {
        return (long) block * (long) blockSize;
    }

    private int getBlock(long pos) {
        return (int) (pos / blockSize);
    }

    private int getBlockCount(int length) {
        return DataUtil.roundUpInt(length, blockSize) / blockSize;
    }

    /**
     * Get the fill rate of the space in percent. The value 0 means the space is
     * completely free, and 100 means it is completely full.
     *
     * @return the fill rate (0 - 100)
     */
    int getFillRate() {
        return getProjectedFillRate(0);
    }

    /**
     * Calculates a prospective fill rate, which store would have after rewrite
     * of sparsely populated chunk(s) and evacuation of still live data into a
     * new chunk.
     *
     * @param vacatedBlocks
     *            number of blocks vacated  as a result of live data evacuation less
     *            number of blocks in prospective chunk with evacuated live data
     * @return prospective fill rate (0 - 100)
     */
    int getProjectedFillRate(int vacatedBlocks) {
        // it's not bullet-proof against race condition but should be good enough
        // to get approximation without holding a store lock
        int usedBlocks;
        int totalBlocks;
        // to prevent infinite loop, which I saw once
        int cnt = 3;
        do {
            if (--cnt == 0) {
                return 100;
            }
            totalBlocks = set.length();
            usedBlocks = set.cardinality();
        } while (usedBlocks > totalBlocks);
        usedBlocks -= firstFreeBlock + vacatedBlocks;
        totalBlocks -= firstFreeBlock;
        return usedBlocks == 0 ? 0 : (int)((100L * usedBlocks + totalBlocks - 1) / totalBlocks);
    }

    int getFreeBlockCount() {
        return set.length() - set.cardinality();
    }

    /**
     * Get the position of the first free space.
     *
     * @return the position.
     */
    long getFirstFree() {
        return getPos(set.nextClearBit(0));
    }

    /**
     * Get the position of the last (infinite) free space.
     *
     * @return the position.
     */
    long getLastFree() {
        return getPos(getAfterLastBlock());
    }

    /**
     * Get the index of the first block after last occupied one.
     * It marks the beginning of the last (infinite) free space.
     *
     * @return block index
     */
    int getAfterLastBlock() {
        return set.previousSetBit(set.size() - 1) + 1;
    }

    /**
     * Calculates relative "priority" for chunk to be moved.
     *
     * @param block where chunk starts
     * @return priority, bigger number indicate that chunk need to be moved sooner
     */
    int getMovePriority(int block) {
        // The most desirable chunks to move are the ones sitting within
        // a relatively short span of occupied blocks which is surrounded
        // from both sides by relatively long free spans
        int prevEnd = set.previousClearBit(block);
        int freeSize;
        if (prevEnd < 0) {
            prevEnd = firstFreeBlock;
            freeSize = 0;
        } else {
            freeSize = prevEnd - set.previousSetBit(prevEnd);
        }

        int nextStart = set.nextClearBit(block);
        int nextEnd = set.nextSetBit(nextStart);
        if (nextEnd >= 0) {
            freeSize += nextEnd - nextStart;
        }
        return (nextStart - prevEnd - 1) * 1000 / (freeSize + 1);
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        if (DETAILED_INFO) {
            int onCount = 0, offCount = 0;
            int on = 0;
            for (int i = 0; i < set.length(); i++) {
                if (set.get(i)) {
                    onCount++;
                    on++;
                } else {
                    offCount++;
                }
                if ((i & 1023) == 1023) {
                    buff.append(String.format("%3x", on)).append(' ');
                    on = 0;
                }
            }
            buff.append('\n')
                    .append(" on ").append(onCount).append(" off ").append(offCount)
                    .append(' ').append(100 * onCount / (onCount+offCount)).append("% used ");
        }
        buff.append('[');
        for (int i = 0;;) {
            if (i > 0) {
                buff.append(", ");
            }
            int start = set.nextClearBit(i);
            buff.append(Integer.toHexString(start)).append('-');
            int end = set.nextSetBit(start + 1);
            if (end < 0) {
                break;
            }
            buff.append(Integer.toHexString(end - 1));
            i = end + 1;
        }
        buff.append(']');
        return buff.toString();
    }
}