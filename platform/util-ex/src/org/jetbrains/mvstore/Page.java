/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.jpountz.lz4.LZ4Compressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A page (a node or a leaf).
 * <p>
 * For b-tree nodes, the key at a given index is larger than the largest key of
 * the child at the same index.
 * <p>
 * File format:
 * page length (including length): int
 * check value: short
 * map id: varInt
 * number of keys: varInt
 * type: byte (0: leaf, 1: node; +2: compressed)
 * compressed: bytes saved (varInt)
 * keys
 * leaf: values (one for each key)
 * node: children (1 more than keys)
 */
abstract class Page<K,V> implements Cloneable {
    /**
     * The type for node page.
     */
    protected static final int PAGE_TYPE_NODE = 1;

    /**
     * The bit mask for compressed pages (compression level fast).
     */
    static final int PAGE_COMPRESSED = 2;

    /**
     * The bit mask for compressed pages (compression level high).
     */
    static final int PAGE_COMPRESSED_HIGH = 2 + 4;

    /**
     * Map this page belongs to
     */
    final MVMap<K,V> map;

    /**
     * Position of this page's saved image within a Chunk
     * or 0 if this page has not been saved yet
     * or 1 if this page has not been saved yet, but already removed
     * This "removed" flag is to keep track of pages that concurrently
     * changed while they are being stored, in which case the live bookkeeping
     * needs to be aware of such cases.
     * Field need to be volatile to avoid races between saving thread setting it
     * and other thread reading it to access the page.
     * On top of this update atomicity is required so removal mark and saved position
     * can be set concurrently.
     *
     * @see DataUtil#getPageInfo(int, int, int, int) for field format details
     */
    private volatile long position;

    /**
     * Sequential 0-based number of the page within containing chunk.
     */
    int pageNo = -1;

    /**
     * The last result of a find operation is cached.
     */
    private int cachedCompare;

    /**
     * The estimated memory used in persistent case, IN_MEMORY marker value otherwise.
     */
    protected int memory;

    protected final KeyManager<K> keyManager;

    /**
     * Updater for pos field, which can be updated when page is saved,
     * but can be concurrently marked as removed
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<Page> pageInfoUpdater =
      AtomicLongFieldUpdater.newUpdater(Page.class, "position");

    /**
     * The estimated number of bytes used per base page.
     */
    protected static final int PAGE_MEMORY =
      DataUtil.VAR_INT_MAX_SIZE * 3 + // map id, page number, key count
      Byte.BYTES + // type
      Integer.BYTES + // page length
      Short.BYTES; // check

    /**
     * Marker value for memory field, meaning that memory accounting is replaced by key count.
     */
    protected static final int IN_MEMORY = Integer.MIN_VALUE;

    protected Page(MVMap<K,V> map, long info, ByteBuf buf, int chunkId) {
        this.map = map;
        this.position = info;

        int offset = DataUtil.getPageOffset(info);

        int pageStartReaderIndex = buf.readerIndex();
        int pageLength = buf.readInt();
        int remaining = buf.readableBytes() + 4;
        if (pageLength > remaining || pageLength < 4) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "File corrupted in chunk " +
                                       chunkId + ", expected page length 4.." + remaining + ", got " + pageLength);
        }

        // restrain readers to only this page
        int writerIndexToRestore = buf.writerIndex();
        buf.writerIndex(pageStartReaderIndex + pageLength);

        short check = buf.readShort();
        int checkTest = DataUtil.getCheckValue(chunkId) ^ DataUtil.getCheckValue(offset) ^ DataUtil.getCheckValue(pageLength);
        if (check != (short)checkTest) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "File corrupted in chunk " + chunkId + ", expected check value " + checkTest + ", got " + check);
        }

        int mapId = IntBitPacker.readVar(buf);
        if (mapId != this.map.getId()) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "File corrupted in chunk " + chunkId + ", expected map id " + this.map.getId() + ", got " + mapId);
        }

        pageNo = IntBitPacker.readVar(buf);
        int keyCount = IntBitPacker.readVar(buf);
        int type = buf.readByte();
        if (isLeaf() != ((type & 1) == DataUtil.PAGE_TYPE_LEAF)) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "File corrupted in chunk " +
                                       chunkId + ", expected node type " + (isLeaf() ? "0" : "1") + ", got " + type);
        }

        //noinspection AbstractMethodCallInConstructor
        keyManager = readPayload(buf, keyCount, (type & PAGE_COMPRESSED) != 0, pageLength, pageStartReaderIndex);

        buf.writerIndex(writerIndexToRestore);
        //noinspection AbstractMethodCallInConstructor
        memory = calculateMemory();
    }

    protected Page(@NotNull MVMap<K,V> map, @NotNull KeyManager<K> keyManager, int memory) {
        this.map = map;
        this.keyManager = keyManager;
        this.memory = memory;
    }

    /**
     * Create a new, empty leaf page.
     *
     * @param map the map
     * @return the new page
     */
    static <K,V> Page<K,V> createEmptyLeaf(MVMap<K,V> map) {
        //noinspection unchecked
        return new LeafPage<>(map, map.getKeyType().createEmptyManager(map), (V[])ObjectKeyManager.EMPTY_OBJECT_ARRAY, 0);
    }

    /**
     * Create a new leaf page. The arrays are not cloned.
     *
     * @param map the map
     * @param keys the keys
     * @param values the values
     * @return the page
     */
    static <K,V> LeafPage<K,V> createLeaf(MVMap<K, V> map, @NotNull KeyManager<K> keys, V[] values) {
        return new LeafPage<>(map, keys, values, PAGE_MEMORY + map.getValueType().getMemory(values));
    }

    /**
     * Get the value for the given key, or null if not found.
     * Search is done in the tree rooted at given page.
     *
     * @param key the key
     * @param p the root page
     * @return the value, or null if not found
     */
    static <K,V> V get(Page<K,V> p, K key) {
        while (true) {
            int index = p.binarySearch(key);
            if (p.isLeaf()) {
                return index >= 0 ? p.getValue(index) : null;
            } else if (index++ < 0) {
                index = -index;
            }
            p = p.getChildPage(index);
        }
    }

    /**
     * Get the id of the page's owner map
     * @return id
     */
    public final int getMapId() {
        return map.getId();
    }

    /**
     * Create a copy of this page with potentially different owning map.
     * This is used exclusively during bulk map copying.
     * Child page references for nodes are cleared (re-pointed to an empty page)
     * to be filled-in later to copying procedure. This way it can be saved
     * mid-process without tree integrity violation
     *
     * @param map new map to own resulting page
     * @return the page
     */
    abstract Page<K,V> copy(MVMap<K,V> map);

    /**
     * Get the key at the given index.
     *
     * @param index the index
     * @return the key
     */
    public K getKey(int index) {
        return keyManager.getKey(index);
    }

    /**
     * Get the child page at the given index.
     *
     * @param index the index
     * @return the child page
     */
    public abstract Page<K,V> getChildPage(int index);

    /**
     * Get the position of the child.
     *
     * @param index the index
     * @return the position
     */
    public abstract long getChildPagePos(int index);

    /**
     * Get the value at the given index.
     *
     * @param index the index
     * @return the value
     */
    public abstract V getValue(int index);

    /**
     * Get the number of keys in this page.
     *
     * @return the number of keys
     */
    public final int getKeyCount() {
        return keyManager.getKeyCount();
    }

    /**
     * Check whether this is a leaf page.
     *
     * @return true if it is a leaf
     */
    public final boolean isLeaf() {
        return getNodeType() == DataUtil.PAGE_TYPE_LEAF;
    }

    public abstract int getNodeType();

    /**
     * Get the position of the page
     *
     * @return the position
     */
    public final long getPosition() {
        return position;
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        dump(buff);
        buff.append(')');
        return buff.toString();
    }

    /**
     * Dump debug data for this page.
     *
     * @param buff append buffer
     */
    protected void dump(StringBuilder buff) {
        buff.append("Page(identityHashCode=").append(System.identityHashCode(this));
        long position = this.position;
        buff.append(", position=");
        if (position == 0) {
            buff.append("not saved");
        } else if (position == 1) {
            buff.append("removed");
        } else {
            buff.append(position);
            buff.append(", offset=").append(DataUtil.getPageOffset(position));
        }
        if (isSaved()) {
            buff.append(", chunk: ").append(DataUtil.getPageChunkId(position));
        }
    }

    /**
     * Search the key in this page using a binary search. Instead of always
     * starting the search in the middle, the last found index is cached.
     * <p>
     * If the key was found, the returned value is the index in the key array.
     * If not found, the returned value is negative, where -1 means the provided
     * key is smaller than any keys in this page. See also Arrays.binarySearch.
     *
     * @param key the key
     * @return the value or null
     */
    final int binarySearch(K key) {
        int result = keyManager.binarySearch(key, map, cachedCompare);
        cachedCompare = result < 0 ? ~result : result + 1;
        return result;
    }

    abstract Page<K,V> split(int at, boolean left);

    /**
     * Get the total number of key-value pairs, including child pages.
     *
     * @return the number of key-value pairs
     */
    public abstract long getTotalCount();

    /**
     * Get the number of key-value pairs for a given child.
     *
     * @param index the child index
     * @return the descendant count
     */
    abstract long getCounts(int index);

    /**
     * Remove the key and value (or child) at the given index.
     *
     * @param index the index
     */
    public abstract Page<K, V> remove(int index);

    /**
     * Read the page payload from the buffer.
     */
    protected abstract void writePayload(ByteBuf buf, int keyCount);

    protected abstract KeyManager<K> readPayload(ByteBuf buf, int keyCount, boolean isCompressed, int pageLength, int pageStartReaderIndex);

    public final boolean isSaved() {
        return DataUtil.isPageSaved(position);
    }

    public final boolean isRemoved() {
        return DataUtil.isPageRemoved(position);
    }

    /**
     * Mark this page as removed "in memory". That means that only adjustment of
     * "unsaved memory" amount is required. On the other hand, if page was
     * persisted, it's removal should be reflected in occupancy of the
     * containing chunk.
     *
     * @return true if it was marked by this call or has been marked already,
     *         false if page has been saved already.
     */
    private boolean markAsRemoved() {
        assert getTotalCount() > 0 : this;
        long pagePos;
        do {
            pagePos = position;
            if (DataUtil.isPageSaved(pagePos)) {
                return false;
            }
            assert !DataUtil.isPageRemoved(pagePos);
        } while (!pageInfoUpdater.compareAndSet(this, 0L, 1L));
        return true;
    }

    /**
     * Store the page and update the position.
     *
     * @param chunk the chunk
     * @param buf the target buffer
     * @param toc prospective table of content
     * @return the position of the buffer just after the type
     */
    protected final int write(Chunk chunk, ByteBuf buf, LongArrayList toc) {
        pageNo = toc.size();
        int start = buf.writerIndex();
        // placeholder for pageLength (int)
        // placeholder for check (short)
        buf.writerIndex(buf.writerIndex() + 4 + 2);

      IntBitPacker.writeVar(buf, map.getId());
      IntBitPacker.writeVar(buf, pageNo);

      int keyCount = keyManager.getKeyCount();
      IntBitPacker.writeVar(buf, keyCount);
      int typePosition = buf.writerIndex();
        int type = isLeaf() ? DataUtil.PAGE_TYPE_LEAF : PAGE_TYPE_NODE;
        buf.writeByte((byte)type);

        writePayload(buf, keyCount);

        int pageLength = buf.writerIndex() - start;
        int chunkId = chunk.id;
        int check = DataUtil.getCheckValue(chunkId) ^ DataUtil.getCheckValue(start) ^ DataUtil.getCheckValue(pageLength);
        buf.setInt(start, pageLength);
        buf.setShort(start + 4, check);

        long tocElement = DataUtil.getTocElement(map.getId(), start, pageLength, type);
        toc.add(tocElement);

        if (isSaved()) {
            throw new MVStoreException(MVStoreException.ERROR_INTERNAL, "Page already stored");
        }

        long pageInfo = DataUtil.getPageInfo(chunkId, tocElement);
        boolean isDeleted = isRemoved();
        while (!pageInfoUpdater.compareAndSet(this, isDeleted ? 1L : 0L, pageInfo)) {
            isDeleted = isRemoved();
        }

        MVStore store = map.getStore();
        store.cachePage(this);

        int pageLengthEncoded = DataUtil.getPageMaxLength(position);
        boolean singleWriter = map.isSingleWriter();
        chunk.accountForWrittenPage(pageLengthEncoded, singleWriter);
        if (isDeleted) {
            store.accountForRemovedPage(pageInfo, chunk.version + 1, singleWriter, pageNo);
        }
        return typePosition + 1;
    }

    protected static void compressData(ByteBuf dataBuffer,
                                       int type,
                                       int typePosition,
                                       int dataStart,
                                       MVStore store,
                                       int uncompressedLength,
                                       int compressionLevel) {
        LZ4Compressor compressor = store.getCompressor();
        int compressType = compressionLevel == 1 ? PAGE_COMPRESSED : PAGE_COMPRESSED_HIGH;
        // see Lz4FrameEncoder for example
        int maxCompressedLength = compressor.maxCompressedLength(uncompressedLength);
        ByteBuf outBuffer = PooledByteBufAllocator.DEFAULT.ioBuffer(maxCompressedLength);
        try {
            ByteBuffer inNioBuffer = DataUtil.getNioBuffer(dataBuffer, dataStart, uncompressedLength);
            // no need to check nioBufferCount for outBuffer as it is allocated with specified length
            assert outBuffer.nioBufferCount() == 1;
            ByteBuffer outNioBuffer = outBuffer.internalNioBuffer(0, maxCompressedLength);
            int compressedLength = compressor.compress(inNioBuffer, inNioBuffer.position(), uncompressedLength,
                                                       outNioBuffer, outNioBuffer.position(), maxCompressedLength);
            int delta = uncompressedLength - compressedLength;
            // some threshold
            if (delta < 32) {
                return;
            }

            dataBuffer.setByte(typePosition, (byte)(type | compressType));
            dataBuffer.writerIndex(dataStart);
            IntBitPacker.writeVar(dataBuffer, delta);
            //System.out.println(" > " + delta + "  " + uncompressedLength + "  " + compressedLength);
            dataBuffer.writeBytes(outBuffer, 0, compressedLength);
        }
        finally {
            outBuffer.release();
        }
    }

    /**
     * Store this page and all children that are changed, in reverse order, and
     * update the position and the children.
     * @param chunk the chunk
     * @param buf the target buffer
     * @param toc prospective table of content
     */
    abstract void writeUnsavedRecursive(Chunk chunk, ByteBuf buf, LongArrayList toc);

    /**
     * Unlink the children recursively after all data is written.
     */
    abstract void releaseSavedPages();

    public abstract int getRawChildPageCount();

    protected final boolean isPersistent() {
        return memory != IN_MEMORY;
    }

    public final int getMemory() {
        return memory + keyManager.getSerializedDataSize();
    }

    public int getUnsavedMemory() {
        return isSaved() ? 0 : getMemory();
    }

    /**
     * Calculate estimated memory used in persistent case.
     *
     * @return memory in bytes
     */
    protected abstract int calculateMemory();

    public boolean isComplete() {
        return true;
    }

    /**
     * Called when done with copying page.
     */
    public void setComplete() {}

    /**
     * Make accounting changes (chunk occupancy or "unsaved" RAM), related to
     * this page removal.
     *
     * @param version at which page was removed
     * @return amount (negative), by which "unsaved memory" should be adjusted,
     *         if page is unsaved one, and 0 for page that was already saved, or
     *         in case of non-persistent map
     */
    public final int removePage(long version) {
        if (isPersistent() && getTotalCount() > 0) {
            MVStore store = map.getStore();
            if (!markAsRemoved()) { // only if it has been saved already
                long pagePos = position;
                store.accountForRemovedPage(pagePos, version, map.isSingleWriter(), pageNo);
            } else {
                return -getMemory();
            }
        }
        return 0;
    }

    /**
     * Extend path from a given CursorPos chain to "prepend point" in a B-tree, rooted at this Page.
     *
     * @param cursorPos presumably pointing to this Page (null if real root), to build upon
     * @return new head of the CursorPos chain
     */
    public abstract CursorPos<K,V> getPrependCursorPos(CursorPos<K,V> cursorPos);

    /**
     * Extend path from a given CursorPos chain to "append point" in a B-tree, rooted at this Page.
     *
     * @param cursorPos presumably pointing to this Page (null if real root), to build upon
     * @return new head of the CursorPos chain
     */
    public abstract CursorPos<K,V> getAppendCursorPos(CursorPos<K,V> cursorPos);

    /**
     * Remove all page data recursively.
     * @param version at which page got removed
     * @return adjustment for "unsaved memory" amount
     */
    public abstract int removeAllRecursive(long version);

    /**
     * Create array for keys storage.
     *
     * @param size number of entries
     * @return values array
     */
    public final K[] createKeyStorage(int size) {
        return map.getKeyType().createStorage(size);
    }

    /**
     * Create an array of page references.
     *
     * @param size the number of entries
     * @return the array
     */
    @SuppressWarnings("unchecked")
    public static <K,V> PageReference<K,V>[] createRefStorage(int size) {
        return new PageReference[size];
    }

    /**
     * A pointer to a page, either in-memory or using a page position.
     */
    static final class PageReference<K,V> {
        /**
         * Singleton object used when arrays of PageReference have not yet been filled.
         */
        @SuppressWarnings("rawtypes")
        static final PageReference EMPTY = new PageReference<>(null, 0, 0);

        /**
         * The page info, if known, or 0.
         */
        private long info;

        /**
         * The page, if in memory, or null.
         */
        private Page<K,V> page;

        /**
         * The descendant count for this child page.
         */
        final long totalCount;

        /**
         * Get an empty page reference.
         *
         * @return the page reference
         */
        @SuppressWarnings("unchecked")
        public static <X,Y> PageReference<X,Y> empty() {
            return EMPTY;
        }

        PageReference(Page<K,V> page) {
            this(page, page.getPosition(), page.getTotalCount());
        }

        PageReference(long info, long totalCount) {
            this(null, info, totalCount);
            assert DataUtil.isPageSaved(info);
        }

        private PageReference(Page<K,V> page, long info, long totalCount) {
            this.page = page;
            this.info = info;
            this.totalCount = totalCount;
        }

        public Page<K,V> getPage() {
            return page;
        }

        /**
         * Clear if necessary, reference to the actual child Page object,
         * so it can be garbage collected if not actively used elsewhere.
         * Reference is cleared only if corresponding page was already saved on a disk.
         */
        void clearPageReference() {
            if (page != null) {
                page.releaseSavedPages();
                assert page.isSaved() || !page.isComplete();
                if (page.isSaved()) {
                    assert info == page.getPosition();
                    assert totalCount == page.getTotalCount() : totalCount + " != " + page.getTotalCount();
                    page = null;
                }
            }
        }

        long getPos() {
            return info;
        }

        /**
         * Re-acquire position from in-memory page.
         */
        void resetPos() {
            Page<K,V> p = page;
            if (p != null && p.isSaved()) {
                info = p.getPosition();
                assert totalCount == p.getTotalCount();
            }
        }

        @Override
        public String toString() {
            return "totalCount=" + totalCount + ", info=" + (info == 0 ? "0" : DataUtil.getPageChunkId(info) +
                                                                               (page == null ? "" : "/" + page.pageNo) +
                                                                               "-" + DataUtil.getPageOffset(info) + ":" + DataUtil.getPageMaxLength(
              info)) +
                   ((page == null ? DataUtil.getPageType(info) == 0 : page.isLeaf()) ? " leaf" : " node") +
                   ", page:{" + page + "}";
        }
    }
}
