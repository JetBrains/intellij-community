/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.roaringbitmap.RoaringBitmap;

import java.io.IOException;
import java.util.Comparator;

/**
 * A chunk of data, containing one or multiple pages.
 * <p>
 * Chunks are page aligned (each page is usually 4096 bytes).
 * There are at most 67 million (2^26) chunks,
 * each chunk is at most 2 GB large.
 */
final class Chunk {
    // maxLenLive is encoded in chunk header
    private static final int FLAG_LIVE_MAX = 1;
    // pageCountLive is encoded in chunk header
    private static final int FLAG_UNUSED = 2;
    private static final int FLAG_UNUSED_AT_VERSION = 4;
    private static final int FLAG_PIN_COUNT = 8;
    private static final int FLAG_OCCUPANCY = 16;

    /**
     * The maximum chunk id.
     */
    static final int MAX_ID = (1 << 26) - 1;

    /**
     * The maximum length of a chunk metadata, in bytes.
     * 105 max size of fields + rest for occupancy byte array
     */
    static final int MAX_METADATA_LENGTH = 4096;
    private static final int MAX_OCCUPANCY_LENGTH = MAX_METADATA_LENGTH - 101;

    static final int HEADER_LENGTH = 76;

    /**
     * The length of the chunk footer
     */
    static final int FOOTER_LENGTH = 24;

    /**
     * The chunk id.
     */
    public final int id;

    /**
     * The start block number within the file.
     */
    public volatile long block;

    /**
     * The length in number of blocks.
     */
    int blockCount;

    /**
     * The total number of pages in this chunk.
     */
    int pageCount;

    /**
     * Offset (from the beginning of the chunk) for the table of content.
     * Table of content is holding a long value for each page in the chunk.
     * This value consists of map id, page offset, page length and page type.
     * Format is the same as page's position id, but with map id replacing chunk id.
     *
     * @see DataUtil#getTocElement(int, int, int, int) for field format details
     */
    int tocPos;

    /**
     * Collection of "deleted" flags for all pages in the chunk.
     */
    private @Nullable RoaringBitmap occupancy;

    /**
     * The sum of the max length of all pages.
     */
    public long maxLen;

    /**
     * The sum of the max length of all pages that are in use.
     */
    public long maxLenLive;

    /**
     * The garbage collection priority. Priority 0 means it needs to be
     * collected, a high value means low priority.
     */
    int collectPriority;

    /**
     * The root page info of layout map.
     */
    long layoutRootPageInfo;

    /**
     * The root page info of chunk map.
     */
    long chunkMapRootPageInfo;

    /**
     * The version stored in this chunk.
     */
    public long version;

    /**
     * When this chunk was created, in milliseconds after the store was created.
     */
    public long time;

    /**
     * When this chunk was no longer needed, in milliseconds after the store was
     * created. After this, the chunk is kept alive a bit longer (in case it is
     * referenced in older versions).
     */
    public long unused;

    /**
     * Version of the store at which chunk become unused and therefore can be
     * considered "dead" and collected after this version is no longer in use.
     */
    long unusedAtVersion;

    /**
     * The last used map id.
     */
    public int mapId;

    /**
     * The predicted position of the next chunk.
     */
    long next;

    /**
     * Number of live pinned pages.
     */
    private int pinCount;

    Chunk(int id, long block, long version) {
        this(id);
        this.block = block;
        this.version = version;
    }

    Chunk(int id) {
        this.id = id;
        if (id <=  0) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "Invalid chunk id " + id);
        }
    }

    boolean isLivePage(int pageNumber) {
        return occupancy == null || !occupancy.contains(pageNumber);
    }

    int getLivePageCount() {
        return occupancy == null ? pageCount : pageCount - occupancy.getCardinality();
    }

    private Chunk(int id, ByteBuf buf, int flags) {
        if (id <=  0) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "Invalid chunk id " + id);
        }

        this.id = id;

        mapId = buf.readInt();
        block = buf.readLong();
        tocPos = buf.readInt();

        blockCount = buf.readInt();
        maxLen = buf.readLong();
        maxLenLive = (flags & FLAG_LIVE_MAX) == FLAG_LIVE_MAX ? buf.readLong() : maxLen;

        pageCount = buf.readInt();

        next = buf.readLong();
        layoutRootPageInfo = buf.readLong();
        chunkMapRootPageInfo = buf.readLong();

        time = buf.readLong();
        version = buf.readLong();
        if ((flags & FLAG_UNUSED) == FLAG_UNUSED) {
            unused = buf.readLong();
        }
        if ((flags & FLAG_UNUSED_AT_VERSION) == FLAG_UNUSED_AT_VERSION) {
            unusedAtVersion = buf.readLong();
        }
        if ((flags & FLAG_PIN_COUNT) == FLAG_PIN_COUNT) {
            pinCount = buf.readInt();
        }

        if ((flags & FLAG_OCCUPANCY) == FLAG_OCCUPANCY) {
            int readerIndex = buf.readerIndex();
            occupancy = new RoaringBitmap();
            try {
                occupancy.deserialize(DataUtil.getNioBuffer(buf, readerIndex, Math.min(buf.readableBytes(), MAX_OCCUPANCY_LENGTH)));
            }
            catch (IOException e) {
                throw new MVStoreException(MVStoreException.ERROR_INTERNAL, e);
            }
            buf.readerIndex(readerIndex + occupancy.serializedSizeInBytes());
        }
        else {
            occupancy = null;
        }
    }

    /**
     * Read the header from the byte buffer.
     *
     * @param buf the source buffer
     * @param start the start of the chunk in the file
     * @return the chunk
     */
    static Chunk readChunkHeader(ByteBuf buf, long start) {
        try {
            return new Chunk(buf.readInt(), buf, 0);
        } catch (Exception e) {
            // there could be various reasons
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "File corrupt reading chunk at position " + start, e);
        }
    }

    static Chunk readMetadata(int chunkId, ByteBuf buf) {
        try {
            int flags = buf.readByte();
            return new Chunk(chunkId, buf, flags);
        } catch (Exception e) {
            // there could be various reasons
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "File corrupt reading chunk", e);
        }
    }

    void writeMetadata(ByteBuf buf) {
        // 1 byte
        byte flags = 0;
        int flagPosition = buf.writerIndex();
        buf.writerIndex(flagPosition + 1);

        // 1 + 16 = 17 bytes
        buf.writeInt(mapId);
        buf.writeLong(block);
        buf.writeInt(tocPos);

        // 17 + 20 = 37 bytes
        buf.writeInt(blockCount);
        buf.writeLong(maxLen);
        if (maxLen != maxLenLive) {
            flags |= FLAG_LIVE_MAX;
            buf.writeLong(maxLenLive);
        }

        // 37 + 4 = 41 bytes
        buf.writeInt(pageCount);

        // 41 + 24 = 65 bytes
        // next is changed after first writing of chunk - cannot be optional
        buf.writeLong(next);
        buf.writeLong(layoutRootPageInfo);
        buf.writeLong(chunkMapRootPageInfo);

        // 65 + 32 = 97 bytes
        buf.writeLong(time);
        buf.writeLong(version);

        if (unused != 0) {
            flags |= FLAG_UNUSED;
            buf.writeLong(unused);
        }
        if (unusedAtVersion != 0) {
            flags |= FLAG_UNUSED_AT_VERSION;
            buf.writeLong(unusedAtVersion);
        }

        // 97 + 4 = 101 bytes
        if (pinCount > 0) {
            flags |= FLAG_PIN_COUNT;
            buf.writeInt(pinCount);
        }

        RoaringBitmap occupancy = this.occupancy;
        if (occupancy != null && !occupancy.isEmpty()) {
            flags |= FLAG_OCCUPANCY;
            occupancy.runOptimize();
            int size = occupancy.serializedSizeInBytes();
            assert size <= MAX_OCCUPANCY_LENGTH : size + " > " + MAX_OCCUPANCY_LENGTH;
            occupancy.serialize(DataUtil.getNioBuffer(buf, buf.writerIndex(), size));
            buf.writerIndex(buf.writerIndex() + size);
        }

        buf.setByte(flagPosition, flags);
    }

    void writeHeader(ByteBuf buf) {
        assert occupancy == null && maxLenLive == maxLen && unused == 0 && unusedAtVersion == 0 && pinCount == 0;

        buf.ensureWritable(HEADER_LENGTH);

        // 0 + 20 = 20 bytes
        buf.writeInt(id);
        buf.writeInt(mapId);
        buf.writeLong(block);
        buf.writeInt(tocPos);

        // 20 + 12 = 32 bytes
        buf.writeInt(blockCount);
        buf.writeLong(maxLen);

        // 32 + 4 = 36 bytes
        buf.writeInt(pageCount);

        // 36 + 24 = 60 bytes
        // next is changed after first writing of chunk - cannot be optional
        buf.writeLong(next);
        buf.writeLong(layoutRootPageInfo);
        buf.writeLong(chunkMapRootPageInfo);

        // 60 + 16 = 76 bytes
        buf.writeLong(time);
        buf.writeLong(version);
    }

    /**
     * Calculate the fill rate in %. 0 means empty, 100 means full.
     *
     * @return the fill rate
     */
    int getFillRate() {
        assert maxLenLive <= maxLen : maxLenLive + " > " + maxLen;
        if (maxLenLive <= 0) {
            return 0;
        } else if (maxLenLive == maxLen) {
            return 100;
        }
        return 1 + (int) (98 * maxLenLive / maxLen);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Chunk && ((Chunk) o).id == id;
    }

    void writeFooter(@NotNull ByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeInt(id);
        buf.writeLong(block);
        buf.writeLong(version);
        buf.writeInt(DataUtil.getFletcher32(buf, start, 20));
    }

    boolean isSaved() {
        return block != Long.MAX_VALUE;
    }

    boolean isLive() {
        return occupancy == null || pageCount != occupancy.getCardinality();
    }

    boolean isRewritable() {
        return isSaved()
                && isLive()
                && occupancy != null && !occupancy.isEmpty() // not fully occupied
                && isEvacuatable();
    }

    private boolean isEvacuatable() {
        return pinCount == 0;
    }

    /**
     * Read a page of data into a ByteBuffer.
     *
     * @param fileStore to use
     * @param pos page pos
     * @return ByteBuf containing page data. Don't forget to release it.
     */
    ByteBuf readBufferForPage(FileStore fileStore, int offset, long pos) {
        assert isSaved() : this;
        while (true) {
            long originalBlock = block;
            try {
                long filePos = originalBlock * MVStore.BLOCK_SIZE;
                long maxPos = filePos + (long)blockCount * MVStore.BLOCK_SIZE;
                filePos += offset;
                if (filePos < 0) {
                    throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                               "Negative position " + filePos + " (position=" + pos + ", chunk=" + toString() + ")");
                }

                int length = DataUtil.getPageMaxLength(pos);
                if (length == DataUtil.PAGE_LARGE) {
                    ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(128);
                    try {
                        // read the first bytes to figure out actual length
                        fileStore.readFully(buf, filePos, 128);
                        length = buf.readInt();
                    }
                    finally {
                        buf.release();
                    }
                }

                length = Math.min((int)(maxPos - filePos), length);
                if (length < 0) {
                    throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                               "Illegal page length " + length + " reading at " + filePos + "; max pos "+ maxPos);
                }

                ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(length, length);
                boolean fail = true;
                try {
                    fileStore.readFully(buf, filePos, length);
                    fail = false;
                }
                finally {
                    if (fail) {
                        buf.release();
                    }
                }

                // maybe changed during read
                if (originalBlock == block) {
                    return buf;
                } else {
                    buf.release();
                }
            } catch (MVStoreException e) {
                if (originalBlock == block) {
                    throw e;
                }
            }
        }
    }

    LongArrayList readToC(FileStore fileStore) {
        assert isSaved() : this;
        assert tocPos > 0;
        while (true) {
            long originalBlock = block;
            int pageCount = this.pageCount;
            int length = pageCount * Long.BYTES;
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(length, length);
            try {
                fileStore.readFully(buf, originalBlock * MVStore.BLOCK_SIZE + tocPos, length);
                long[] toc = new long[pageCount];
                DataUtil.readLongArray(toc, buf, pageCount);
                // maybe changed during read
                if (originalBlock == block) {
                    return LongArrayList.wrap(toc);
                }
            }
            catch (MVStoreException e) {
                if (originalBlock == block) {
                    throw e;
                }
            }
            finally {
                buf.release();
            }
        }
    }

    /**
     * Modifies internal state to reflect the fact that one more page is stored
     * within this chunk.
     *  @param pageLengthOnDisk
     *            size of the page
     * @param singleWriter
     *            indicates whether page belongs to append mode capable map
     *            (single writer map). Such pages are "pinned" to the chunk,
     *            they can't be evacuated (moved to a different chunk) while
     */
    void accountForWrittenPage(int pageLengthOnDisk, boolean singleWriter) {
        maxLen += pageLengthOnDisk;
        pageCount++;
        maxLenLive += pageLengthOnDisk;
        if (singleWriter) {
            pinCount++;
        }
    }

    /**
     * Modifies internal state to reflect the fact that one the pages within
     * this chunk was removed from the map.
     *
     * @param pageNo
     *            sequential page number within the chunk
     * @param pageLength
     *            on disk of the removed page
     * @param pinned
     *            whether removed page was pinned
     * @param now
     *            is a moment in time (since creation of the store), when
     *            removal is recorded, and retention period starts
     * @param version
     *            at which page was removed
     * @return true if all of the pages, this chunk contains, were already
     *         removed, and false otherwise
     */
    boolean accountForRemovedPage(int pageNo, int pageLength, boolean pinned, long now, long version) {
        assert isSaved() : this;
        assert pageNo >= 0 && pageNo < pageCount : pageNo + " // " +  pageCount;
        if (occupancy == null) {
            occupancy = new RoaringBitmap();
        }
        else {
            assert !occupancy.contains(pageNo) : pageNo + " " + this + " " + occupancy;
        }
        occupancy.add(pageNo);

        maxLenLive -= pageLength;
        if (pinned) {
            pinCount--;
        }

        if (unusedAtVersion < version) {
            unusedAtVersion = version;
        }

        assert pinCount >= 0 : this;
        assert pinCount <= (pageCount - occupancy.getCardinality()) : this;
        assert maxLenLive >= 0 : this;
        assert ((pageCount - occupancy.getCardinality()) == 0) == (maxLenLive == 0) : this;

        if (!isLive()) {
            unused = now;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Chunk(" +
               "id=" + id +
               ", mapId=" + mapId +
               ", block=" + block +
               ", blockCount=" + blockCount +
               ", pageCount=" + pageCount +
               ", occupancy=" + occupancy +
               ", tocPos=" + tocPos +
               ", maxLen=" + maxLen +
               ", maxLenLive=" + maxLenLive +
               ", collectPriority=" + collectPriority +
               ", layoutRootPageInfo=" + layoutRootPageInfo +
               ", chunkMapRootPageInfo=" + chunkMapRootPageInfo +
               ", version=" + version +
               ", time=" + time +
               ", unused=" + unused +
               ", unusedAtVersion=" + unusedAtVersion +
               ", next=" + next +
               ", pinCount=" + pinCount +
               ')';
    }

    static final class PositionComparator implements Comparator<Chunk> {
        public static final Comparator<Chunk> INSTANCE = new PositionComparator();

        private PositionComparator() {}

        @Override
        public int compare(Chunk one, Chunk two) {
            return Long.compare(one.block, two.block);
        }
    }
}

