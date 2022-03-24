/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.intellij.openapi.util.EmptyRunnable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.mvstore.type.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import static org.jetbrains.mvstore.MVMap.INITIAL_VERSION;

/*

TODO:

Documentation
- rolling docs review: at "Metadata Map"
- better document that writes are in background thread
- better document how to do non-unique indexes
- document pluggable store and OffHeapStore

TransactionStore:
- ability to disable the transaction log,
    if there is only one connection

MVStore:
- better and clearer memory usage accounting rules
    (heap memory versus disk memory), so that even there is
    never an out of memory
    even for a small heap, and so that chunks
    are still relatively big on average
- make sure serialization / deserialization errors don't corrupt the file
- test and possibly improve compact operation (for large dbs)
- automated 'kill process' and 'power failure' test
- defragment (re-creating maps, specially those with small pages)
- store number of write operations per page (maybe defragment
    if much different than count)
- r-tree: nearest neighbor search
- use a small object value cache (StringCache), test on Android
    for default serialization
- MVStoreTool.dump should dump the data if possible;
    possibly using a callback for serialization
- implement a sharded map (in one store, multiple stores)
    to support concurrent updates and writes, and very large maps
- to save space when persisting very small transactions,
    use a transaction log where only the deltas are stored
- serialization for lists, sets, sets, sorted sets, maps, sorted maps
- maybe rename 'rollback' to 'revert' to distinguish from transactions
- support other compression algorithms (deflate, LZ4,...)
- remove features that are not really needed; simplify the code
    possibly using a separate layer or tools
    (retainVersion?)
- optional pluggable checksum mechanism (per page), which
    requires that everything is a page (including headers)
- rename "store" to "save", as "store" is used in "storeVersion"
- rename setStoreVersion to setDataVersion, setSchemaVersion or similar
- temporary file storage
- simple rollback method (rollback to last committed version)
- MVMap to implement SortedMap, then NavigableMap
- storage that splits database into multiple files,
    to speed up compact and allow using trim
    (by truncating / deleting empty files)
- add new feature to the file system API to avoid copying data
    (reads that returns a ByteBuffer instead of writing into one)
    for memory mapped files and off-heap storage
- support log structured merge style operations (blind writes)
    using one map per level plus bloom filter
- have a strict call order MVStore -> MVMap -> Page -> FileStore
- autocommit commits, stores, and compacts from time to time;
    the background thread should wait at least 90% of the
    configured write delay to store changes
- compact* should also store uncommitted changes (if there are any)
- write a LSM-tree (log structured merge tree) utility on top of the MVStore
    with blind writes and/or a bloom filter that
    internally uses regular maps and merge sort
- chunk metadata: maybe split into static and variable,
    or use a small page size for metadata
- data type "string": maybe use prefix compression for keys
- test chunk id rollover
- feature to auto-compact from time to time and on close
- compact very small chunks
- Page: to save memory, combine keys & values into one array
    (also children & counts). Maybe remove some other
    fields (childrenCount for example)
- Support SortedMap for MVMap
- compact: copy whole pages (without having to open all maps)
- maybe change the length code to have lower gaps
- test with very low limits (such as: short chunks, small pages)
- maybe allow to read beyond the retention time:
    when compacting, move live pages in old chunks
    to a map (possibly the metadata map) -
    this requires a change in the compaction code, plus
    a map lookup when reading old data; also, this
    old data map needs to be cleaned up somehow;
    maybe using an additional timeout
*/

/**
 * A persistent storage for maps.
 */
@SuppressWarnings("TypeParameterExtendsFinalClass")
public final class MVStore implements AutoCloseable {
    public static final boolean ASSERT_MODE = Boolean.getBoolean("mvstore.assert.mode");

    // id of map name to map id map
    private static final int MAP_NAME_MAP_ID = 0;
    private static final int LAYOUT_MAP_ID = 1;
    private static final int CHUNK_MAP_ID = 2;

    /**
     * The block size (physical sector size) of the disk. The store header is
     * written twice, one copy in each block, to ensure it survives a crash.
     */
    static final int BLOCK_SIZE = 4 * 1024;

    private static final byte FORMAT_WRITE = 3;
    private static final byte FORMAT_READ = 3;

    // first 4 numbers reserved for internal maps
    private static final int MIN_USER_MAP_ID = 5;

    /**
     * Store is open.
     */
    private static final int STATE_OPEN = 0;

    /**
     * Store is about to close now, but is still operational.
     * Outstanding store operation by background writer or other thread may be in progress.
     * New updates must not be initiated, unless they are part of a closing procedure itself.
     */
    private static final int STATE_STOPPING = 1;

    /**
     * Store is closing now, and any operation on it may fail.
     */
    private static final int STATE_CLOSING = 2;

    /**
     * Store is closed.
     */
    private static final int STATE_CLOSED = 3;

    /**
     * Lock which governs access to major store operations: store(), close(), ...
     * It serves as a replacement for synchronized(this), except it allows for
     * non-blocking lock attempts.
     */
    private final ReentrantLock storeLock = new ReentrantLock(true);
    private final ReentrantLock serializationLock = new ReentrantLock(true);
    private final ReentrantLock saveChunkLock = new ReentrantLock(true);

    /**
     * Single-threaded executor for serialization of the store snapshot into ByteBuffer
     */
    private final ThreadPoolExecutor serializationExecutor;

    private volatile boolean reuseSpace = true;

    private volatile int state = STATE_OPEN;

    private final FileStore fileStore;

    private final boolean closeFileStoreClose;

    private final Builder config;

    private final Cache<Long, Page<?,?>> nonLeafPageCache;
    private final Cache<Long, Page<?,?>> leafPageCache;
    final long nonLeafPageSplitSize;
    final long leafPageSplitSize;

    /**
     * Cache for chunks "Table of Content" used to translate page's
     * sequential number within containing chunk into byte position
     * within chunk's image. Cache keyed by chunk id.
     */
    private final Cache<Integer, LongArrayList> chunkIdToToC;

    /**
     * The newest chunk. If nothing was stored yet, this field is not set.
     */
    private volatile Chunk lastChunk;

    /**
     * The map of chunks.
     */
    private final ConcurrentHashMap<Integer, Chunk> chunks = new ConcurrentHashMap<>();

    private final Queue<RemovedPageInfo> removedPages = new PriorityBlockingQueue<>();

    private final Deque<Chunk> deadChunks = new ArrayDeque<>();

    private long updateCounter = 0;
    private long updateAttemptCounter = 0;

    /**
     * The layout map. Contains root pages info for all maps.
     * This is relatively fast changing part of metadata.
     */
    private final MVMap<Integer, Long> layout;

    /**
     * The chunk layout map. Contains chunks metadata for all maps.
     * This is relatively fast changing part of metadata.
     */
    private final MVMap<Integer, byte[]> chunkIdToChunkMetadata;

    /**
     * The metadata map. Holds name -> MapMetadata mapping for all maps.
     * This is relatively slow changing part of metadata.
     */
    private final MVMap<AsciiString, MapMetadata> mapNameToMetadata;

    private final MVMap<?, ?>[] metaMaps;
    private final ConcurrentHashMap<Integer, MVMap<?, ?>> maps = new ConcurrentHashMap<>();

  @Override
  public String toString() {
    return fileStore.toString();
  }

  public static final class StoreHeader {
        short format = FORMAT_WRITE;
        short formatRead = FORMAT_READ;

        /**
         * The time the store was created, in milliseconds since 1970.
         */
        long creationTime;
        long lastChunkVersion;
        int lastChunkId;
        long lastBlockNumber;

        int blockSize = BLOCK_SIZE;

        boolean cleanShutdown;

        void read(ByteBuf buf) {
            byte firstByte = buf.readByte();
            byte secondByte = buf.readByte();
            assert firstByte == 'm' && secondByte == 'v';
            format = buf.readUnsignedByte();
            formatRead = buf.readUnsignedByte();

            creationTime = buf.readLong();

            lastChunkVersion = buf.readLong();
            lastChunkId = buf.readInt();
            lastBlockNumber = buf.readLong();

            blockSize = buf.readInt();
            if (blockSize == 0) {
                blockSize = BLOCK_SIZE;
            }

            cleanShutdown = buf.readBoolean();
        }

        void write(ByteBuf buf) {
            buf.writeByte('m');
            buf.writeByte('v');
            buf.writeByte(format);
            buf.writeByte(formatRead);

            buf.writeLong(creationTime);

            buf.writeLong(lastChunkVersion);
            buf.writeInt(lastChunkId);
            buf.writeLong(lastBlockNumber);

            buf.writeInt(blockSize);

            buf.writeBoolean(cleanShutdown);
        }

        @Override
        public String toString() {
            return "StoreHeader(" +
                   "format=" + format +
                   ", formatRead=" + formatRead +
                   ", creationTime=" + creationTime +
                   ", lastChunkVersion=" + lastChunkVersion +
                   ", lastChunkId=" + lastChunkId +
                   ", lastBlockNumber=" + lastBlockNumber +
                   ", blockSize=" + blockSize +
                   ", cleanShutdown=" + cleanShutdown +
                   ')';
        }
    }

    private StoreHeader storeHeader = new StoreHeader();

    private final AtomicInteger lastMapId = new AtomicInteger(MIN_USER_MAP_ID - 1);

    private int lastChunkId;

    private LZ4Compressor compressor;
    private LZ4FastDecompressor decompressor;

    private volatile long currentVersion;

    /**
     * Oldest store version in use. All version beyond this can be safely dropped
     */
    private final AtomicLong oldestVersionToKeep = new AtomicLong();

    /**
     * Ordered collection of all version usage counters for all versions starting
     * from oldestVersionToKeep and up to current.
     */
    private final Deque<TxCounter> versions = new LinkedList<>();

    /**
     * Counter of open transactions for the latest (current) store version
     */
    private volatile TxCounter currentTxCounter = new TxCounter(currentVersion);

    /**
     * The estimated memory used by unsaved pages. This number is not accurate,
     * also because it may be changed concurrently, and because temporary pages
     * are counted.
     */
    private int unsavedMemory;
    private volatile boolean saveNeeded;

    /**
     * How long to retain old, persisted chunks, in milliseconds. For larger or
     * equal to zero, a chunk is never directly overwritten if unused, but
     * instead, the unused field is set. If smaller zero, chunks are directly
     * overwritten if unused.
     */
    private int retentionTime;

    private long lastCommitTime;

    /**
     * The version of the current store operation (if any).
     */
    private volatile long currentStoreVersion = INITIAL_VERSION;

    private volatile boolean metaChanged;

    /**
     * The delay in milliseconds to automatically commit and write changes.
     */
    private int autoCommitDelay;

    private long autoCompactLastFileOpCount;

    private volatile MVStoreException panicException;

    private long lastTimeAbsolute;

    private long leafCount;
    private long nonLeafCount;


    /**
     * Create and open the store.
     *
     * @param config the configuration to use
     * @throws MVStoreException if the file is corrupt, or an exception
     *             occurred while opening
     */
    public MVStore(FileStore fileStore, Builder config) {
        closeFileStoreClose = true;
        this.fileStore = fileStore;

        this.config = config;

        if (fileStore != null && config.nonLeafPageCacheSize > 0) {
            long maxNonLeafWeight = config.nonLeafPageCacheSize * 1024L * 1024L;
            long maxLeafWeight = config.leafPageCacheSize * 1024L * 1024L;
            nonLeafPageCache = createPageCache(maxNonLeafWeight, config.recordCacheStats);
            leafPageCache = createPageCache(maxLeafWeight, config.recordCacheStats);

            Caffeine<Integer, LongArrayList> chunksToCacheBuilder = Caffeine.newBuilder()
              .maximumWeight((1024L * 1024L) / Long.BYTES)
              .weigher((Integer key, List<?> value) -> value.size());
            if (config.recordCacheStats) {
                chunksToCacheBuilder.recordStats();
            }
          chunkIdToToC = chunksToCacheBuilder.build();

            // make sure pages will fit into cache
            nonLeafPageSplitSize = Math.min(maxNonLeafWeight, config.pageSplitSize);
            leafPageSplitSize = Math.min(maxLeafWeight, config.pageSplitSize);
        }
        else {
            nonLeafPageCache = null;
            leafPageCache = null;
          chunkIdToToC = null;
            nonLeafPageSplitSize = Long.MAX_VALUE;
            leafPageSplitSize = Long.MAX_VALUE;
        }

        layout = new MVMap<>(this, LAYOUT_MAP_ID, IntDataType.INSTANCE, LongDataType.INSTANCE);
        chunkIdToChunkMetadata = new MVMap<>(this, CHUNK_MAP_ID, IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        if (fileStore == null) {
            serializationExecutor = null;
            mapNameToMetadata = openMapNameMap();
        }
        else {
            retentionTime = fileStore.getDefaultRetentionTime();
            // there is no need to lock store here, since it is not opened (or even created) yet,
            // just to make some assertions happy, when they ensure single-threaded access
            storeLock.lock();
            try {
                saveChunkLock.lock();
                try {
                    int storeHeaderSize = 2 * BLOCK_SIZE;
                    ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(storeHeaderSize, storeHeaderSize);
                    try {
                        if (fileStore.size() > storeHeaderSize) {
                            readStoreHeader(buf);
                        }
                        else {
                            // if less or equal than store header, it is empty db file - recreate
                            StoreHeader storeHeader = this.storeHeader;
                            storeHeader.creationTime = getTimeAbsolute();
                            storeHeader.format = FORMAT_WRITE;
                            storeHeader.blockSize = BLOCK_SIZE;
                            currentVersion = INITIAL_VERSION + 1;
                            layout.setRootPageInfo(0, INITIAL_VERSION);
                            chunkIdToChunkMetadata.setRootPageInfo(0, INITIAL_VERSION);
                            if (!fileStore.isReadOnly()) {
                                writeStoreHeader(buf);
                            }
                        }
                    }
                    finally {
                        buf.release();
                    }
                }
                finally {
                    saveChunkLock.unlock();
                }
            } finally {
                unlockAndCheckPanicCondition();
            }
            lastCommitTime = getTimeSinceCreation();

            mapNameToMetadata = openMapNameMap();
            Int2ObjectMap<MapMetadata> idToMetadata = scrubMetaMap();
            scrubLayoutMap(idToMetadata);

            autoCommitDelay = config.autoCommitDelay;
            if (!fileStore.isReadOnly() && autoCommitDelay > 0 && isOpen()) {
              serializationExecutor = new ThreadPoolExecutor(1, 1,
                                                             5L, TimeUnit.SECONDS,
                                                             new LinkedBlockingQueue<>(), new ThreadFactory() {
                @Override
                public Thread newThread(@NotNull Runnable r) {
                  Thread thread = new Thread(r, "MVStore Serialization");
                  thread.setPriority(Thread.MIN_PRIORITY);
                  return thread;
                }
              });
            } else {
                serializationExecutor = null;
            }
        }
        metaMaps = new MVMap[]{mapNameToMetadata, layout, chunkIdToChunkMetadata};
        onVersionChange(currentVersion);
    }

    private static Cache<Long, Page<?, ?>> createPageCache(long maxSizeInBytes, boolean recordCacheStats) {
        Caffeine<Long, Page<?, ?>> cacheBuilder = Caffeine.newBuilder()
          .maximumWeight(maxSizeInBytes)
          .weigher((Long key, Page<?, ?> value) -> value.getMemory());
        if (recordCacheStats) {
            cacheBuilder.recordStats();
        }
        return cacheBuilder.build();
    }

    private @NotNull MVMap<AsciiString, MapMetadata> openMapNameMap() {
        MVMap<AsciiString, MapMetadata> map = new MVMap<>(this, MAP_NAME_MAP_ID, AsciiStringDataType.INSTANCE, new MapMetadata.MapMetadataSerializer());
        map.setRootPageInfo(getRootPageInfo(map.getId()), currentVersion - 1);
        return map;
    }

    private void scrubLayoutMap(@NotNull Int2ObjectFunction<MapMetadata> idToMetadata) {
        Set<Integer> keysToRemove = null;

        // remove roots of non-existent maps (leftover after unfinished map removal)
        for (Iterator<Integer> it = layout.keyIterator(null); it.hasNext();) {
            Integer mapId = it.next();
            if (mapId >= MIN_USER_MAP_ID && !idToMetadata.containsKey(mapId.intValue())) {
                if (keysToRemove == null) {
                    keysToRemove = new HashSet<>();
                }
                keysToRemove.add(mapId);
            }
        }

        if (keysToRemove != null) {
            for (Integer key : keysToRemove) {
                layout.remove(key);
            }
        }
    }

    private @NotNull Int2ObjectMap<MapMetadata> scrubMetaMap() {
        Set<AsciiString> keysToRemove = null;

        // ensure that there is only one name mapped to each id
        // this could be a leftover of an unfinished map rename
        int size = mapNameToMetadata.size();
        if (size == 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectMap<MapMetadata> idToMetadata = new Int2ObjectOpenHashMap<>(size);
        Int2ObjectMap<AsciiString> idToName = new Int2ObjectOpenHashMap<>(size);

        int maxMapId = lastMapId.get();
        Cursor<AsciiString, MapMetadata> cursor = mapNameToMetadata.cursor(null);
        while (cursor.hasNext()) {
            AsciiString name = cursor.next();
            MapMetadata metadata = cursor.getValue();
            int id = metadata.id;

            MapMetadata existing = idToMetadata.putIfAbsent(id, metadata);
            if (existing != null) {
                if (keysToRemove == null) {
                    keysToRemove = new HashSet<>();
                }

                if (metadata.createVersion <= existing.createVersion) {
                    keysToRemove.add(name);
                    continue;
                }
                else {
                    // remove old name
                    keysToRemove.add(idToName.get(id));
                    idToMetadata.put(id, metadata);
                }
            }

            idToName.put(id, name);
            if (maxMapId < id) {
                maxMapId = id;
            }
        }

        // ensure that last map id is not smaller than any existing map id
        if (maxMapId > lastMapId.get()) {
            lastMapId.set(maxMapId);
        }

        if (keysToRemove != null) {
            for (AsciiString key : keysToRemove) {
                mapNameToMetadata.remove(key);
                markMetaChanged();
            }
        }
        return idToMetadata;
    }

    private void unlockAndCheckPanicCondition() {
        storeLock.unlock();
        if (getPanicException() != null) {
            closeImmediately();
        }
    }

    private void panic(MVStoreException e) {
        if (isOpen()) {
            handleException(e);
            panicException = e;
        }
        throw e;
    }

    public MVStoreException getPanicException() {
        return panicException;
    }

    /**
     * Open a map with the default settings. The map is automatically create if
     * it does not yet exist. If a map with this name is already open, this map
     * is returned.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param name the name of the map
     * @return the map
     */
    public <K, V> MVMap<K, V> openMap(@NotNull String name, @NotNull KeyableDataType<K> keyType, @NotNull DataType<V> valueType) {
        return openMap(name, new MVMap.Builder<K, V>().keyType(keyType).valueType(valueType));
    }

    /**
     * Open a map with the given builder. The map is automatically create if it
     * does not yet exist. If a map with this name is already open, this map is
     * returned.
     *
     * @param <M> the map type
     * @param <K> the key type
     * @param <V> the value type
     * @param nameAsString the name of the map
     * @param builder the map builder
     * @return the map
     */
    public <M extends MVMap<K, V>, K, V> M openMap(@NotNull CharSequence nameAsString, @NotNull MVMap.MapBuilder<M, K, V> builder) {
        AsciiString name = AsciiString.of(nameAsString);
        MapMetadata metadata = mapNameToMetadata.get(name);
        if (metadata != null) {
            @SuppressWarnings("unchecked")
            M map = (M) getMap(metadata.id);
            if (map == null) {
                map = openMap(metadata, builder);
            }
            assert builder.getKeyType() == null || map.getKeyType().getClass().equals(builder.getKeyType().getClass());
            assert builder.getValueType() == null
                    || map.getValueType().getClass().equals(builder.getValueType().getClass());
            return map;
        } else {
            int id = lastMapId.incrementAndGet();
            assert id >= MIN_USER_MAP_ID && !maps.containsKey(id);

            metadata = new MapMetadata(id, currentVersion);
            M map = builder.create(this, id, metadata);
            mapNameToMetadata.put(name, metadata);
            long lastStoredVersion = currentVersion - 1;
            map.setRootPageInfo(0, lastStoredVersion);
            markMetaChanged();
            @SuppressWarnings("unchecked")
            M existingMap = (M) maps.putIfAbsent(id, map);
            if (existingMap != null) {
                map = existingMap;
            }
            return map;
        }
    }

    /**
     * Open an existing map with the given builder.
     *
     * @param <M> the map type
     * @param <K> the key type
     * @param <V> the value type
     * @param builder the map builder
     * @return the map
     */
    public <M extends MVMap<K, V>, K, V> M openMap(@NotNull MapMetadata metadata, MVMap.MapBuilder<M, K, V> builder) {
        storeLock.lock();
        try {
            int id = metadata.id;
            @SuppressWarnings("unchecked")
            M map = (M) getMap(id);
            if (map == null) {
                map = builder.create(this, id, metadata);
                long root = getRootPageInfo(id);
                long lastStoredVersion = currentVersion - 1;
                map.setRootPageInfo(root, lastStoredVersion);
                maps.put(id, map);
            }
            return map;
        } finally {
            storeLock.unlock();
        }
    }

    /**
     * Get map by id.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param id map id
     * @return Map
     */
    public <K, V> MVMap<K,V> getMap(int id) {
        checkOpen();
        @SuppressWarnings("unchecked")
        MVMap<K, V> map = (MVMap<K, V>) maps.get(id);
        return map;
    }

    /**
     * Get the set of all map names.
     *
     * @return the set of names
     */
    public Set<CharSequence> getMapNames() {
        Set<CharSequence> set = new HashSet<>();
        checkOpen();
        for (Iterator<AsciiString> iterator = mapNameToMetadata.keyIterator(null); iterator.hasNext();) {
            set.add(iterator.next());
        }
        return set;
    }

    /**
     * Get this store's layout map. This data is for informational purposes only. The
     * data is subject to change in future versions.
     * <p>
     * The data in this map should not be modified (changing system data may corrupt the store).
     * <p>
     * The layout map contains the following entries:
     * <pre>
     * chunk.{chunkId} = {chunk metadata}
     * root.{mapId} = {root position}
     * </pre>
     *
     * @return the metadata map
     */
    public MVMap<Integer, Long> getLayoutMap() {
        checkOpen();
        return layout;
    }

    @TestOnly
    public MVMap<AsciiString, MapMetadata> getMetaMap() {
        checkOpen();
        return mapNameToMetadata;
    }

    public MVMap<Integer, byte[]> getChunkMap() {
        checkOpen();
        return chunkIdToChunkMetadata;
    }

    /**
     * Check whether a given map exists.
     *
     * @param name the map name
     * @return true if it exists
     */
    public boolean hasMap(@NotNull CharSequence name) {
        return mapNameToMetadata.containsKey(AsciiString.of(name));
    }

    /**
     * Check whether a given map exists and has data.
     *
     * @param name the map name
     * @return true if it exists and has data.
     */
    public boolean hasData(@NotNull CharSequence name) {
        MapMetadata metadata = mapNameToMetadata.get(AsciiString.of(name));
        return metadata != null && getRootPageInfo(metadata.id) != 0;
    }

    private void markMetaChanged() {
        // changes in the metadata alone are usually not detected, as the meta
        // map is changed after storing
        metaChanged = true;
    }

    private void readStoreHeader(ByteBuf buf) {
        Chunk newest = null;
        boolean assumeCleanShutdown = true;
        boolean validStoreHeader = false;
        // find out which chunk and version are the newest
        // read the first two blocks
        fileStore.readFully(buf, 0, BLOCK_SIZE * 2);
        for (int i = 0; i < 2; i++) {
            // the following can fail for various reasons
            try {
                int headerStart = i * BLOCK_SIZE;
                buf.setIndex(headerStart, 2 * BLOCK_SIZE);
                StoreHeader header = new StoreHeader();
                header.read(buf);
                int headerEnd = buf.readerIndex();
                int checksum = buf.readInt();
                buf.readerIndex(headerStart);
                buf.writerIndex(headerEnd);
                if (DataUtil.getFletcher32(buf, buf.readerIndex(), buf.readableBytes()) != checksum) {
                    assumeCleanShutdown = false;
                    continue;
                }

                // if both header blocks do agree on version
                // we'll continue on happy path - assume that previous shutdown was clean
                assumeCleanShutdown = assumeCleanShutdown && (newest == null || header.lastChunkVersion == newest.version);
                if (newest == null || header.lastChunkVersion > newest.version) {
                    validStoreHeader = true;
                    storeHeader = header;
                    int chunkId = header.lastChunkId;
                    long blockNumber = header.lastBlockNumber;
                    if (blockNumber == 0) {
                        // 0 and 1 blocks are reserved for 2 file headers
                        blockNumber = 2;
                    }

                    // reuse buf - chunk header/footer size is less than store header
                    buf.clear();
                    Chunk test = readChunkHeaderAndFooter(blockNumber, chunkId, buf);
                    if (test != null) {
                        newest = test;
                    }
                }
            }
            catch (Exception e) {
                handleException(e);
                assumeCleanShutdown = false;
            }
        }

        if (!validStoreHeader) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "Store header is corrupt: " + fileStore);
        }

        int blockSize = storeHeader.blockSize;
        if (blockSize != BLOCK_SIZE) {
            throw new MVStoreException(MVStoreException.ERROR_UNSUPPORTED_FORMAT, "Block size " + blockSize + " is currently not supported");
        }

        long format = storeHeader.format;
        if (format > FORMAT_WRITE && !fileStore.isReadOnly()) {
            throw new MVStoreException(MVStoreException.ERROR_UNSUPPORTED_FORMAT,
                                       "The write format " + format + " is larger " +
                                       "than the supported format " + FORMAT_WRITE + ", " +
                                       "and the file was not opened in read-only mode");
        }
        format = storeHeader.formatRead;
        if (format > FORMAT_READ) {
            throw new MVStoreException(MVStoreException.ERROR_UNSUPPORTED_FORMAT,
                                       "The read format " + format + " is larger than the supported format " + FORMAT_READ);
        }

        assumeCleanShutdown = assumeCleanShutdown && newest != null && !config.recoveryMode;
        if (assumeCleanShutdown) {
            assumeCleanShutdown = storeHeader.cleanShutdown;
        }
        chunks.clear();
        long now = System.currentTimeMillis();
        // calculate the year (doesn't have to be exact;
        // we assume 365.25 days per year, * 4 = 1461)
        int year =  1970 + (int) (now / (1000L * 60 * 60 * 6 * 1461));
        if (year < 2014) {
            // if the year is before 2014,
            // we assume the system doesn't have a real-time clock,
            // and we set the creationTime to the past, so that
            // existing chunks are overwritten
            storeHeader.creationTime = now - fileStore.getDefaultRetentionTime();
        } else if (now < storeHeader.creationTime) {
            // the system time was set to the past:
            // we change the creation time
            storeHeader.creationTime = now;
        }

        long fileSize = fileStore.size();
        long blocksInStore = fileSize / BLOCK_SIZE;

        Comparator<Chunk> chunkComparator = (one, two) -> {
            if (two.version == one.version) {
                // out of two copies of the same chunk we prefer the one
                // close to the beginning of file (presumably later version)
                return Long.compare(one.block, two.block);
            }
            return 0;
        };

        if (!assumeCleanShutdown) {
            Chunk tailChunk = discoverChunk(blocksInStore);
            if (tailChunk != null) {
                blocksInStore = tailChunk.block; // for a possible full scan later on
                if (newest == null || tailChunk.version > newest.version) {
                    newest = tailChunk;
                }
            }
        }

        Long2ObjectMap<Chunk> validChunksByLocation = new Long2ObjectOpenHashMap<>();
        if (newest != null) {
            // read the chunk header and footer,
            // and follow the chain of next chunks
            while (true) {
                validChunksByLocation.put(newest.block, newest);
                if (newest.next == 0 || newest.next >= blocksInStore) {
                    // no (valid) next
                    break;
                }
                buf.clear();
                Chunk test = readChunkHeaderAndFooter(newest.next, newest.id + 1, buf);
                if (test == null || test.version <= newest.version) {
                    break;
                }
                // if shutdown was really clean then chain should be empty
                assumeCleanShutdown = false;
                newest = test;
            }
        }

        if (assumeCleanShutdown) {
            // quickly check latest 20 chunks referenced in meta table
            Queue<Chunk> chunksToVerify = new PriorityQueue<>(20, Collections.reverseOrder(chunkComparator));
            try {
                setLastChunk(newest);
                // load the chunk metadata: although meta's root page resides in the lastChunk,
                // traversing meta map might recursively load another chunk(s)
                Cursor<Integer, byte[]> cursor = chunkIdToChunkMetadata.cursor(null);
                while (cursor.hasNext()) {
                    // might be there already, due to meta traversal
                    // see readPage() ... getChunkIfFound()
                    Chunk chunk = chunks.computeIfAbsent(cursor.next(), id -> Chunk.readMetadata(id, Unpooled.wrappedBuffer(cursor.getValue())));
                    assert chunk.version <= currentVersion;
                    chunksToVerify.offer(chunk);
                    if (chunksToVerify.size() == 20) {
                        chunksToVerify.poll();
                    }
                }
                Chunk chunk;
                while (assumeCleanShutdown && (chunk = chunksToVerify.poll()) != null) {
                    buf.clear();
                    Chunk test = readChunkHeaderAndFooter(chunk.block, chunk.id, buf);
                    assumeCleanShutdown = test != null;
                    if (assumeCleanShutdown) {
                        validChunksByLocation.put(test.block, test);
                    }
                }
            } catch(MVStoreException e) {
                handleException(e);
                assumeCleanShutdown = false;
            }
        }

        if (!assumeCleanShutdown) {
            boolean quickRecovery = false;
            if (!config.recoveryMode) {
                // now we know, that previous shutdown did not go well and file
                // is possibly corrupted but there is still hope for a quick
                // recovery

                // this collection will hold potential candidates for lastChunk to fall back to,
                // in order from the most to least likely
                Chunk[] lastChunkCandidates = validChunksByLocation.values().toArray(new Chunk[0]);
                Arrays.sort(lastChunkCandidates, chunkComparator);
                Int2ObjectMap<Chunk> validChunksById = new Int2ObjectOpenHashMap<>(lastChunkCandidates.length);
                for (Chunk chunk : lastChunkCandidates) {
                    validChunksById.put(chunk.id, chunk);
                }
                quickRecovery = findLastChunkWithCompleteValidChunkSet(lastChunkCandidates, validChunksByLocation,
                                                                       validChunksById, false);
            }

            if (!quickRecovery) {
                // scan whole file and try to fetch chunk header and/or footer out of every block
                // matching pairs with nothing in-between are considered as valid chunk
                long block = blocksInStore;
                Chunk tailChunk;
                while ((tailChunk = discoverChunk(block)) != null) {
                    block = tailChunk.block;
                    validChunksByLocation.put(block, tailChunk);
                }

                // this collection will hold potential candidates for lastChunk to fall back to,
                // in order from the most to least likely
                Chunk[] lastChunkCandidates = validChunksByLocation.values().toArray(new Chunk[0]);
                Arrays.sort(lastChunkCandidates, chunkComparator);
                Int2ObjectMap<Chunk> validChunksById = new Int2ObjectOpenHashMap<>(lastChunkCandidates.length);
                for (Chunk chunk : lastChunkCandidates) {
                    validChunksById.put(chunk.id, chunk);
                }
                if (!findLastChunkWithCompleteValidChunkSet(lastChunkCandidates, validChunksByLocation,
                        validChunksById, true) && lastChunk != null) {
                    throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                               "File is corrupted - unable to recover a valid set of chunks");

                }
            }
        }

        fileStore.clear();
        // build the free space list
        for (Chunk c : chunks.values()) {
            if (c.isSaved()) {
                long start = c.block * BLOCK_SIZE;
                int length = c.blockCount * BLOCK_SIZE;
                fileStore.markUsed(start, length);
            }
            if (!c.isLive()) {
                deadChunks.offer(c);
            }
        }

        if (ASSERT_MODE) {
            assert validateFileLength("on open");
        }
    }

    private boolean findLastChunkWithCompleteValidChunkSet(Chunk[] lastChunkCandidates,
                                                           Long2ObjectMap<Chunk> validChunksByLocation,
                                                           Int2ObjectMap<Chunk> validChunksById,
                                                           boolean afterFullScan) {
        // Try candidates for "last chunk" in order from newest to oldest
        // until suitable is found. Suitable one should have meta map
        // where all chunk references point to valid locations.
        for (Chunk chunk : lastChunkCandidates) {
            boolean verified = true;
            ByteBuf buf = null;
            try {
                setLastChunk(chunk);
                // load the chunk metadata: although meta's root page resides in the lastChunk,
                // traversing meta map might recursively load another chunk(s)
                Cursor<Integer, byte[]> cursor = chunkIdToChunkMetadata.cursor(null);
                while (cursor.hasNext()) {
                    Integer id = cursor.next();
                    Chunk c = Chunk.readMetadata(id, Unpooled.wrappedBuffer(cursor.getValue()));
                    assert c.version <= currentVersion;
                    // might be there already, due to meta traversal
                    // see readPage() ... getChunkIfFound()
                    Chunk test = chunks.putIfAbsent(c.id, c);
                    if (test != null) {
                        c = test;
                    }
                    assert chunks.get(c.id) == c;
                    if ((test = validChunksByLocation.get(c.block)) == null || test.id != c.id) {
                        if ((test = validChunksById.get(c.id)) != null) {
                            // We do not have a valid chunk at that location,
                            // but there is a copy of same chunk from original
                            // location.
                            // Chunk header at original location does not have
                            // any dynamic (occupancy) metadata, so it can't be
                            // used here as is, re-point our chunk to original
                            // location instead.
                            c.block = test.block;
                        } else if (c.isLive()) {
                            if (buf == null) {
                                if (!afterFullScan) {
                                    buf = PooledByteBufAllocator.DEFAULT.ioBuffer(Chunk.FOOTER_LENGTH);
                                }
                            } else {
                                buf.clear();
                            }
                            if (afterFullScan || readChunkHeaderAndFooter(c.block, c.id, buf) == null) {
                                // chunk reference is invalid
                                // this "last chunk" candidate is not suitable
                                verified = false;
                                break;
                            }
                        }
                    }
                    if (!c.isLive()) {
                        // we can just remove entry from meta, referencing to this chunk,
                        // but store maybe R/O, and it's not properly started yet,
                        // so lets make this chunk "dead" and taking no space,
                        // and it will be automatically removed later.
                        c.block = Long.MAX_VALUE;
                        c.blockCount = Integer.MAX_VALUE;
                        if (c.unused == 0) {
                            c.unused = storeHeader.creationTime;
                        }
                        if (c.unusedAtVersion == 0) {
                            c.unusedAtVersion = INITIAL_VERSION;
                        }
                    }
                }
            } catch(Exception ignored) {
                verified = false;
            } finally {
                if (buf != null) {
                    buf.release();
                }
            }
            if (verified) {
                return true;
            }
        }
        return false;
    }

    private void setLastChunk(Chunk last) {
        chunks.clear();
        lastChunk = last;
        lastChunkId = 0;
        currentVersion = lastChunkVersion();
        long layoutRootPageInfo = 0;
        long chunkMapRootPageInfo = 0;
        int mapId = MIN_USER_MAP_ID;
        // there is a valid chunk
        if (last != null) {
            lastChunkId = last.id;
            currentVersion = last.version;
            layoutRootPageInfo = last.layoutRootPageInfo;
            chunkMapRootPageInfo = last.chunkMapRootPageInfo;
            mapId = last.mapId;
            chunks.put(last.id, last);
        }
        lastMapId.set(mapId);
        layout.setRootPageInfo(layoutRootPageInfo, currentVersion - 1);
        chunkIdToChunkMetadata.setRootPageInfo(chunkMapRootPageInfo, currentVersion - 1);
    }

    /**
     * Discover a valid chunk, searching file backwards from the given block
     *
     * @param block to start search from (found chunk footer should be no
     *            further than block-1)
     * @return valid chunk or null if none found
     */
    private Chunk discoverChunk(long block) {
        long candidateLocation = Long.MAX_VALUE;
        Chunk candidate = null;
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(Chunk.FOOTER_LENGTH);
        try {
            while (true) {
                if (block == candidateLocation) {
                    return candidate;
                }
                if (block == 2) { // number of blocks occupied by headers
                    return null;
                }

                buf.clear();
                Chunk test = readChunkFooter(block, buf);
                if (test != null) {
                    // if we encounter chunk footer (with or without corresponding header)
                    // in the middle of prospective chunk, stop considering it
                    candidateLocation = Long.MAX_VALUE;
                    test = readChunkHeaderOptionally(test.block, test.id);
                    if (test != null) {
                        // if that footer has a corresponding header,
                        // consider them as a new candidate for a valid chunk
                        candidate = test;
                        candidateLocation = test.block;
                    }
                }

                // if we encounter chunk header without corresponding footer
                // (due to incomplete write?) in the middle of prospective
                // chunk, stop considering it
                if (--block > candidateLocation && readChunkHeaderOptionally(block) != null) {
                    candidateLocation = Long.MAX_VALUE;
                }
            }
        } finally {
          buf.release();
        }
    }


    /**
     * Read a chunk header and footer, and verify the stored data is consistent.
     *
     * @param blockNumber the blockNumber
     * @param expectedId of the chunk
     * @return the chunk, or null if the header or footer don't match or are not
     *         consistent
     */
    private Chunk readChunkHeaderAndFooter(long blockNumber, int expectedId, ByteBuf buf) {
        Chunk header;
        try {
            header = readChunkHeader(blockNumber, buf);
            if (header.block != blockNumber || header.id != expectedId) {
                return null;
            }
        }
        catch (Exception e) {
            handleException(e);
            return null;
        }

        buf.clear();
        Chunk footer = readChunkFooter(blockNumber + header.blockCount, buf);
        return footer == null || footer.id != expectedId || footer.block != header.block ? null : header;
    }

    /**
     * Try to read a chunk footer.
     *
     * @param blockNumber the index of the next block after the chunk
     * @return the chunk, or null if not successful
     */
    private Chunk readChunkFooter(long blockNumber, ByteBuf buf) {
        // the following can fail for various reasons
        try {
            // read the chunk footer of the last block of the file
            long position = blockNumber * BLOCK_SIZE - Chunk.FOOTER_LENGTH;
            if (position < 0) {
                return null;
            }

            fileStore.readFully(buf, position, Chunk.FOOTER_LENGTH);
            int start = buf.readerIndex();
            int chunkId = buf.readInt();
            long chunkBlock = buf.readLong();
            long chunkVersion = buf.readLong();
            int checksum = buf.readInt();
            if (DataUtil.getFletcher32(buf, start, buf.readerIndex() - start - 4) == checksum) {
                return new Chunk(chunkId, chunkBlock, chunkVersion);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeStoreHeader() {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(2 * BLOCK_SIZE);
        try {
            writeStoreHeader(buf);
        } finally {
            buf.release();
        }
    }

    private void writeStoreHeader(ByteBuf buf) {
        StoreHeader storeHeader = this.storeHeader;
        if (lastChunk != null) {
            storeHeader.lastBlockNumber = lastChunk.block;
            storeHeader.lastChunkId = lastChunk.id;
            storeHeader.lastChunkVersion = lastChunk.version;
        }

        buf.clear();
        buf.ensureWritable(2 * BLOCK_SIZE);
        // the store header is written twice, one copy in each block, to ensure it survives a crash
        storeHeader.write(buf);
        int checksum = DataUtil.getFletcher32(buf, buf.readerIndex(), buf.readableBytes());
        buf.writeInt(checksum);

        buf.writeZero(BLOCK_SIZE - buf.writerIndex());
        storeHeader.write(buf);
        buf.writeInt(checksum);
        buf.writeZero((BLOCK_SIZE * 2) - buf.writerIndex());

        write(0, buf);
    }

    private void write(long pos, ByteBuf buf) {
        try {
            fileStore.writeFully(buf, pos);
        } catch (MVStoreException e) {
            panic(e);
        }
    }

    /**
     * Close the file and the store. Unsaved changes are written to disk first.
     */
    @Override
    public void close() {
        closeStore(true, 0);
    }

    /**
     * Close the file and the store. Unsaved changes are written to disk first,
     * and compaction (up to a specified number of milliseconds) is attempted.
     *
     * @param allowedCompactionTime the allowed time for compaction (in
     *            milliseconds)
     */
    public void close(int allowedCompactionTime) {
        closeStore(true, allowedCompactionTime);
    }

    /**
     * Close the file and the store, without writing anything.
     * This will try to stop the background thread (without waiting for it).
     * This method ignores all errors.
     */
    public void closeImmediately() {
        try {
            closeStore(false, 0);
        } catch (Throwable e) {
            handleException(e);
        }
    }

    private void closeStore(boolean normalShutdown, int allowedCompactionTime) {
        // If any other thread have already initiated closure procedure,
        // isClosed() would wait until closure is done and then  we jump out of the loop.
        // This is a subtle difference between !isClosed() and isOpen().
        while (!isClosed()) {
            shutdownExecutor(serializationExecutor);

            storeLock.lock();
            try {
                if (state == STATE_OPEN) {
                    state = STATE_STOPPING;
                    try {
                        try {
                            if (normalShutdown && fileStore != null && !fileStore.isReadOnly()) {
                                for (MVMap<?, ?> map : maps.values()) {
                                    if (map.isClosed()) {
                                        deregisterMapRoot(map.getId());
                                    }
                                }
                                setRetentionTime(0);
                                commit();
                                if (allowedCompactionTime > 0) {
                                    compactFile(allowedCompactionTime);
                                } else if (allowedCompactionTime < 0) {
                                    doMaintenance(config.autoCompactFillRate);
                                }

                                saveChunkLock.lock();
                                try {
                                    shrinkFileIfPossible(0);
                                    storeHeader.cleanShutdown = true;
                                    writeStoreHeader();
                                    sync();
                                    if (ASSERT_MODE) {
                                        assert validateFileLength("on close");
                                    }
                                } finally {
                                    saveChunkLock.unlock();
                                }
                            }

                            state = STATE_CLOSING;

                            // release memory early - this is important when called
                            // because of out of memory
                            clearCaches();
                            for (MVMap<?, ?> m : new ArrayList<>(maps.values())) {
                                m.close();
                            }
                            chunks.clear();
                            maps.clear();
                        } finally {
                            if (fileStore != null && closeFileStoreClose) {
                                fileStore.close();
                            }
                        }
                    } finally {
                        state = STATE_CLOSED;
                    }
                }
            } finally {
                storeLock.unlock();
            }
        }
    }

    private static void shutdownExecutor(@Nullable ThreadPoolExecutor executor) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException ignore) {/**/}
            executor.purge();
        }
    }

    /**
     * Get the chunk for the given position.
     *
     * @param chunkId the chunkId
     * @return the chunk
     */
    private Chunk getChunk(int chunkId) {
      // computeIfAbsent cannot be used - recursive update
      Chunk chunk = chunks.get(chunkId);
      if (chunk != null) {
        return chunk;
      }

      checkOpen();
      byte[] metadata = chunkIdToChunkMetadata.get(chunkId);
      if (metadata == null) {
          throw new MVStoreException(MVStoreException.ERROR_CHUNK_NOT_FOUND, "Chunk " + chunkId + " not found");
      }

      chunk = Chunk.readMetadata(chunkId, Unpooled.wrappedBuffer(metadata));
      if (!chunk.isSaved()) {
          throw new MVStoreException(MVStoreException.ERROR_CHUNK_NOT_FOUND, "Chunk " + chunkId + " is invalid");
      }
      Chunk prev = chunks.putIfAbsent(chunkId, chunk);
      return prev == null ? chunk : prev;
    }

    private void setWriteVersion(long version) {
        for (Iterator<MVMap<?, ?>> iter = maps.values().iterator(); iter.hasNext(); ) {
            MVMap<?, ?> map = iter.next();
            assert map.getId() >= MIN_USER_MAP_ID;
            if (map.setWriteVersion(version) == null) {
                iter.remove();
            }
        }
        for (MVMap<?, ?> map : metaMaps) {
            map.setWriteVersion(version);
        }
        onVersionChange(version);
    }

    /**
     * Unlike regular commit this method returns immediately if there is commit
     * in progress on another thread, otherwise it acts as regular commit.
     *
     * This method may return BEFORE this thread changes are actually persisted!
     *
     * @return the new version (incremented if there were changes)
     */
    public long tryCommit() {
        return tryCommit(null);
    }

    private long tryCommit(@Nullable Predicate<? super MVStore> check) {
        // we need to prevent re-entrance, which may be possible,
        // because meta map is modified within storeNow() and that
        // causes beforeWrite() call with possibility of going back here
        if ((!storeLock.isHeldByCurrentThread() || currentStoreVersion < 0) && storeLock.tryLock()) {
            try {
                if (check == null || check.test(this)) {
                    store(false);
                }
            } finally {
                unlockAndCheckPanicCondition();
            }
        }
        return currentVersion;
    }

    /**
     * Commit the changes.
     * <p>
     * This method does nothing if there are no unsaved changes,
     * otherwise it increments the current version
     * and stores the data (for file based stores).
     * <p>
     * It is not necessary to call this method when auto-commit is enabled (the default
     * setting), as in this case it is automatically called from time to time or
     * when enough changes have accumulated. However, it may still be called to
     * flush all changes to disk.
     * <p>
     * At most one store operation may run at any time.
     *
     * @return the new version (incremented if there were changes)
     */
    public long commit() {
        return commit(null);
    }

    private long commit(@Nullable Predicate<? super MVStore> check) {
        // we need to prevent re-entrance, which may be possible,
        // because meta map is modified within storeNow() and that
        // causes beforeWrite() call with possibility of going back here
        if(!storeLock.isHeldByCurrentThread() || currentStoreVersion < 0) {
            if (serializationExecutor != null && !serializationExecutor.isTerminated()) {
                try {
                  serializationExecutor.submit(EmptyRunnable.INSTANCE).get(10, TimeUnit.MINUTES);
                }
                catch (Exception e) {
                    panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, e));
                }
            }
            storeLock.lock();
            try {
                if (check == null || check.test(this)) {
                    store(true);
                }
            } finally {
                unlockAndCheckPanicCondition();
            }
        }
        return currentVersion;
    }

    private void store(boolean syncWrite) {
        assert storeLock.isHeldByCurrentThread();
        assert !saveChunkLock.isHeldByCurrentThread();
        if (!isOpenOrStopping() || !hasUnsavedChanges()) {
            return;
        }

        dropUnusedChunks();
        try {
            currentStoreVersion = currentVersion;
            if (fileStore == null) {
                //noinspection NonAtomicOperationOnVolatileField
                ++currentVersion;
                setWriteVersion(currentVersion);
                metaChanged = false;
            } else {
                if (fileStore.isReadOnly()) {
                    throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED, "This store is read-only");
                }
                storeNow(syncWrite, 0, () -> reuseSpace ? 0 : getAfterLastBlock());
            }
        } finally {
            // in any case reset the current store version,
            // to allow closing the store
            currentStoreVersion = -1;
        }
    }

    private void storeNow(boolean syncWrite, long reservedLow, LongSupplier reservedHighSupplier) {
        try {
            lastCommitTime = getTimeSinceCreation();
            int currentUnsavedPageCount = unsavedMemory;
            // it is ok, since that path suppose to be single-threaded under storeLock
            //noinspection NonAtomicOperationOnVolatileField
            long version = ++currentVersion;
            List<Page<?,?>> changed = collectChangedMapRoots(version);

            assert storeLock.isHeldByCurrentThread();
            boolean finished = false;
            if (!syncWrite && serializationExecutor != null) {
                try {
                    serializationExecutor.execute(() -> {
                        serializeAndStore(reservedLow, reservedHighSupplier, changed, lastCommitTime, version);
                    });
                    finished = true;
                } catch (RejectedExecutionException ignore) {
                    assert serializationExecutor.isShutdown();
                }
            }
            if (!finished) {
                serializeAndStore(reservedLow, reservedHighSupplier, changed, lastCommitTime, version);
            }

            // some pages might have been changed in the meantime (in the newest version)
            saveNeeded = false;
            unsavedMemory = Math.max(0, unsavedMemory - currentUnsavedPageCount);
        } catch (MVStoreException e) {
            panic(e);
        } catch (Throwable e) {
            panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, e));
        }
    }

    private List<Page<?,?>> collectChangedMapRoots(long version) {
        long lastStoredVersion = version - 2;
        List<Page<?,?>> changed = new ArrayList<>();
        for (Iterator<MVMap<?, ?>> iter = maps.values().iterator(); iter.hasNext(); ) {
            MVMap<?, ?> map = iter.next();
            RootReference<?,?> rootReference = map.setWriteVersion(version);
            if (rootReference == null) {
                iter.remove();
            } else if (map.getCreateVersion() < version && // if map was created after storing started, skip it
                    !map.isVolatile() &&
                    map.hasChangesSince(lastStoredVersion)) {
                assert rootReference.version <= version : rootReference.version + " > " + version;
                addToChanged(changed, rootReference);
            }
        }

        RootReference<?, ?> rootReference = mapNameToMetadata.setWriteVersion(version);
        if (metaChanged || mapNameToMetadata.hasChangesSince(lastStoredVersion)) {
            assert rootReference != null && rootReference.version <= version
              : rootReference == null ? "null" : rootReference.version + " > " + version;
            addToChanged(changed, rootReference);
        }
        return changed;
    }

    private static void addToChanged(List<Page<?, ?>> changed, RootReference<?, ?> rootReference) {
        Page<?, ?> rootPage = rootReference.root;
        // after deletion previously saved leaf may pop up as a root,
        // but we still need to save new root pos in meta
        if (!rootPage.isSaved() || rootPage.isLeaf()) {
            changed.add(rootPage);
        }
    }

    private void serializeAndStore(long reservedLow, LongSupplier reservedHighSupplier,
                                   List<Page<?, ?>> changed, long time, long version) {
        serializationLock.lock();
        boolean isBufReleased = false;
        ByteBuf buf = null;
        try {
            Chunk chunk = createChunk(time, version);
            chunks.put(chunk.id, chunk);

            int memory = Chunk.HEADER_LENGTH + Chunk.FOOTER_LENGTH;
            for (Page<?, ?> page : changed) {
                memory += page.getUnsavedMemory();
            }

            buf = PooledByteBufAllocator.DEFAULT.ioBuffer(DataUtil.roundUpInt(memory, BLOCK_SIZE));
            serializeToBuffer(buf, changed, chunk, reservedLow, reservedHighSupplier);
            isBufReleased = true;
            storeBuffer(chunk, buf, changed);
        } catch (MVStoreException e) {
            panic(e);
        } catch (Throwable e) {
            panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, e));
        } finally {
            try {
                serializationLock.unlock();
            }
            finally {
                if (!isBufReleased && buf != null) {
                    buf.release();
                }
            }
        }
    }

    private Chunk createChunk(long time, long version) {
        int chunkId = this.lastChunkId;
        if (chunkId != 0) {
            chunkId &= Chunk.MAX_ID;
            Chunk lastChunk = chunks.get(chunkId);
            assert lastChunk != null;
            assert lastChunk.isSaved();
            assert lastChunk.version + 1 == version : lastChunk.version + " " +  version;
            // the metadata of the last chunk was not stored so far, and needs to be
            // set now (it's better not to update right after storing, because that
            // would modify the meta map again)
            putChunkMetadata(chunkId, lastChunk);
            // never go backward in time
            time = Math.max(lastChunk.time, time);
        }

        int newChunkId;
        while (true) {
            newChunkId = ++lastChunkId & Chunk.MAX_ID;
            Chunk old = chunks.get(newChunkId);
            if (old == null) {
                break;
            }
            if (!old.isSaved()) {
                panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, "Last block " + old + " not stored, possibly due to out-of-memory"));
            }
        }

        Chunk c = new Chunk(newChunkId);
        c.layoutRootPageInfo = Long.MAX_VALUE;
        c.chunkMapRootPageInfo = Long.MAX_VALUE;
        c.block = Long.MAX_VALUE;
        c.blockCount = Integer.MAX_VALUE;
        c.time = time;
        c.version = version;
        c.next = Long.MAX_VALUE;
        return c;
    }

    private void putChunkMetadata(int chunkId, Chunk lastChunk) {
        byte[] result;
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer(Chunk.MAX_METADATA_LENGTH);
        try {
            lastChunk.writeMetadata(buf);
            result = ByteBufUtil.getBytes(buf);
        }
        finally {
            buf.release();
        }
        chunkIdToChunkMetadata.put(chunkId, result);
    }

    private void serializeToBuffer(ByteBuf buf, List<Page<?, ?>> changed, Chunk chunk,
                                   long reservedLow, LongSupplier reservedHighSupplier) {
        buf.writerIndex(buf.writerIndex() + Chunk.HEADER_LENGTH);

        long version = chunk.version;
        LongArrayList toc = new LongArrayList();
        for (Page<?,?> p : changed) {
            int mapId = p.getMapId();
            if (p.getTotalCount() == 0) {
                layout.remove(mapId);
            } else {
                p.writeUnsavedRecursive(chunk, buf, toc);
                layout.put(mapId, p.getPosition());
            }
        }

        acceptChunkOccupancyChanges(chunk.time, version);

        RootReference<?, ?> chunkMapRootReference = chunkIdToChunkMetadata.setWriteVersion(version);
        assert chunkMapRootReference != null;
        assert chunkMapRootReference.version == version : chunkMapRootReference.version + " != " + version;

        RootReference<Integer, Long> layoutRootReference = layout.setWriteVersion(version);
        assert layoutRootReference != null;
        assert layoutRootReference.version == version : layoutRootReference.version + " != " + version;

        metaChanged = false;

        acceptChunkOccupancyChanges(chunk.time, version);

        onVersionChange(version);

        Page<?, ?> chunkMapRoot = chunkMapRootReference.root;
        if (chunkIdToChunkMetadata.hasChangesSince(version - 2)) {
            if (!chunkMapRoot.isSaved() || chunkMapRoot.isLeaf()) {
                chunkMapRoot.writeUnsavedRecursive(chunk, buf, toc);
                changed.add(chunkMapRoot);
            }
        }
        chunk.chunkMapRootPageInfo = chunkMapRoot.getPosition();

        Page<Integer, Long> layoutRoot = layoutRootReference.root;
        layoutRoot.writeUnsavedRecursive(chunk, buf, toc);
        chunk.layoutRootPageInfo = layoutRoot.getPosition();
        changed.add(layoutRoot);

        // last allocated map id should be captured after the meta map was saved, because
        // this will ensure that concurrently created map, which made it into meta before save,
        // will have it's id reflected in mapId field of currently written chunk
        chunk.mapId = lastMapId.get();
        writeToC(buf, chunk, toc);
        int chunkLength = buf.writerIndex();

        // add the chunk footer header and round to the next block
        int length = DataUtil.roundUpInt(chunkLength + Chunk.FOOTER_LENGTH, BLOCK_SIZE);

        saveChunkLock.lock();
        try {
            long reservedHigh = reservedHighSupplier.getAsLong();
            long filePosition = fileStore.allocate(length, reservedLow, reservedHigh);
            chunk.blockCount = length / BLOCK_SIZE;
            chunk.block = filePosition / BLOCK_SIZE;
            if (ASSERT_MODE) {
                assert validateFileLength(chunk);
            }
            // calculate and set the likely next position
            if (reservedLow > 0 || reservedHigh == reservedLow) {
                chunk.next = fileStore.predictAllocation(chunk.blockCount, 0, 0);
            } else {
                // just after this chunk
                chunk.next = 0;
            }
            buf.writerIndex(0);
            chunk.writeHeader(buf);
            buf.ensureWritable(length);
            buf.writerIndex(length - Chunk.FOOTER_LENGTH);
            chunk.writeFooter(buf);
        } finally {
            saveChunkLock.unlock();
        }
    }

    private void writeToC(ByteBuf buf, Chunk chunk, LongArrayList toc) {
        chunk.tocPos = buf.writerIndex();
        long[] ids = toc.elements();
        int size = toc.size();
        if (size == 0) {
            return;
        }

        int length = size * Long.BYTES;
        buf.ensureWritable(length);
        // don't use var long - large value in any case
        DataUtil.writeLongArray(ids, buf, size);
        for (int i = 0; i < size; i++) {
            if (DataUtil.isLeafPage(ids[i])) {
                ++leafCount;
            }
            else {
                ++nonLeafCount;
            }
        }
        chunkIdToToC.put(chunk.id, toc);
    }

    private void storeBuffer(Chunk chunk, ByteBuf buf, Collection<Page<?, ?>> changed) {
        saveChunkLock.lock();
        boolean isBufReleased = false;
        try {
            buf.readerIndex(0);
            long filePosition = chunk.block * BLOCK_SIZE;
            fileStore.writeFully(buf, filePosition);
            // end of the used space is not necessarily the end of the file
            boolean storeAtEndOfFile = filePosition + buf.writerIndex() >= fileStore.size();
            boolean writeStoreHeader = isWriteStoreHeader(chunk, storeAtEndOfFile);
            lastChunk = chunk;
            if (writeStoreHeader) {
                writeStoreHeader(buf);
            }

            isBufReleased = true;
            buf.release();

            if (!storeAtEndOfFile) {
                // may only shrink after the store header was written
                shrinkFileIfPossible(1);
            }
        } catch (MVStoreException e) {
            panic(e);
        } catch (Throwable e) {
            panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, e));
        } finally {
            try {
                saveChunkLock.unlock();
            }
            finally {
                if (!isBufReleased) {
                    buf.release();
                }
            }
        }

        for (Page<?, ?> p : changed) {
            p.releaseSavedPages();
        }
    }

    private boolean isWriteStoreHeader(Chunk c, boolean storeAtEndOfFile) {
        // whether we need to write the store header
        boolean writeStoreHeader = false;
        if (!storeAtEndOfFile) {
            Chunk lastChunk = this.lastChunk;
            if (lastChunk == null) {
                writeStoreHeader = true;
            } else if (lastChunk.next != c.block) {
                // the last prediction did not matched
                writeStoreHeader = true;
            } else {
                if (lastChunk.version - storeHeader.lastChunkVersion > 20) {
                    // we write after at least every 20 versions
                    writeStoreHeader = true;
                } else {
                    for (int chunkId = storeHeader.lastChunkId;
                            !writeStoreHeader && chunkId <= lastChunk.id; ++chunkId) {
                        // one of the chunks in between
                        // was removed
                        writeStoreHeader = !chunks.containsKey(chunkId);
                    }
                }
            }
        }

        if (storeHeader.cleanShutdown) {
            writeStoreHeader = true;
        }
        return writeStoreHeader;
    }

    private static boolean canOverwriteChunk(Chunk c, long oldestVersionToKeep) {
        return !c.isLive() && c.unusedAtVersion < oldestVersionToKeep;
    }

    private boolean isSeasonedChunk(Chunk chunk, long time) {
        return retentionTime < 0 || chunk.time + retentionTime <= time;
    }

    private long getTimeSinceCreation() {
        return Math.max(0, getTimeAbsolute() - storeHeader.creationTime);
    }

    private long getTimeAbsolute() {
        long now = System.currentTimeMillis();
        if (lastTimeAbsolute != 0 && now < lastTimeAbsolute) {
            // time seems to have run backwards - this can happen
            // when the system time is adjusted, for example
            // on a leap second
            now = lastTimeAbsolute;
        } else {
            lastTimeAbsolute = now;
        }
        return now;
    }

    /**
     * Apply the freed space to the chunk metadata. The metadata is updated, but
     * completely free chunks are not removed from the set of chunks, and the
     * disk space is not yet marked as free. They are queued instead and wait until
     * their usage is over.
     */
    private void acceptChunkOccupancyChanges(long time, long version) {
        assert serializationLock.isHeldByCurrentThread();
        if (lastChunk == null) {
            return;
        }

        Set<Chunk> modifiedChunks = null;
        ByteBuf buf = null;
        try {
            while (true) {
                RemovedPageInfo rpi;
                while ((rpi = removedPages.peek()) != null && rpi.version < version) {
                    rpi = removedPages.poll();  // could be different from the peeked one
                    assert rpi != null;         // since nobody else retrieves from queue
                    assert rpi.version < version : rpi + " < " + version;
                    int chunkId = rpi.getPageChunkId();
                    Chunk chunk = chunks.get(chunkId);
                    assert !isOpen() || chunk != null : chunkId;
                    if (chunk != null) {
                        if (modifiedChunks == null) {
                            modifiedChunks = new HashSet<>();
                        }
                        modifiedChunks.add(chunk);
                        if (chunk.accountForRemovedPage(rpi.getPageNo(), rpi.getPageLength(),
                                                        rpi.isPinned(), time, rpi.version)) {
                            deadChunks.offer(chunk);
                        }
                    }
                }
                if (modifiedChunks == null || modifiedChunks.isEmpty()) {
                    return;
                }

                if (buf == null) {
                    buf = PooledByteBufAllocator.DEFAULT.heapBuffer(Chunk.MAX_METADATA_LENGTH);
                }
                for (Chunk chunk : modifiedChunks) {
                    buf.clear();
                    chunk.writeMetadata(buf);
                    chunkIdToChunkMetadata.put(chunk.id, ByteBufUtil.getBytes(buf));
                }
                modifiedChunks.clear();
            }
        }
        finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * Shrink the file if possible, and if at least a given percentage can be
     * saved.
     *
     * @param minPercent the minimum percentage to save
     */
    private void shrinkFileIfPossible(int minPercent) {
        assert saveChunkLock.isHeldByCurrentThread();
        if (fileStore.isReadOnly()) {
            return;
        }
        long end = getFileLengthInUse();
        long fileSize = fileStore.size();
        if (end >= fileSize) {
            return;
        }
        if (minPercent > 0 && fileSize - end < BLOCK_SIZE) {
            return;
        }
        int savedPercent = (int) (100 - (end * 100 / fileSize));
        if (savedPercent < minPercent) {
            return;
        }
        if (isOpenOrStopping()) {
            sync();
        }
        fileStore.truncate(end);
    }

    /**
     * Get the position right after the last used byte.
     *
     * @return the position
     */
    private long getFileLengthInUse() {
        assert saveChunkLock.isHeldByCurrentThread();
        long result = fileStore.getFileLengthInUse();
        assert result == measureFileLengthInUse() : result + " != " + measureFileLengthInUse();
        return result;
    }

    /**
     * Get the index of the first block after last occupied one.
     * It marks the beginning of the last (infinite) free space.
     *
     * @return block index
     */
    private long getAfterLastBlock() {
        assert saveChunkLock.isHeldByCurrentThread();
        return fileStore.getAfterLastBlock();
    }

    private long measureFileLengthInUse() {
        assert saveChunkLock.isHeldByCurrentThread();
        long size = 2;
        for (Chunk c : chunks.values()) {
            if (c.isSaved()) {
                size = Math.max(size, c.block + c.blockCount);
            }
        }
        return size * BLOCK_SIZE;
    }

    /**
     * Check whether there are any unsaved changes.
     *
     * @return if there are any changes
     */
    public boolean hasUnsavedChanges() {
        if (metaChanged) {
            return true;
        }
        long lastStoredVersion = currentVersion - 1;
        for (MVMap<?, ?> m : maps.values()) {
            if (!m.isClosed()) {
                if(m.hasChangesSince(lastStoredVersion)) {
                    return true;
                }
            }
        }
        return layout.hasChangesSince(lastStoredVersion) && lastStoredVersion > INITIAL_VERSION;
    }

    private Chunk readChunkHeader(long blockNumber, ByteBuf buf) {
        long p = blockNumber * BLOCK_SIZE;
        fileStore.readFully(buf, p, Chunk.HEADER_LENGTH);
        return Chunk.readChunkHeader(buf, p);
    }

    private Chunk readChunkHeaderOptionally(long blockNumber) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(Chunk.HEADER_LENGTH);
        try {
            Chunk chunk = readChunkHeader(blockNumber, buf);
            return chunk.block == blockNumber ? chunk : null;
        }
        catch (Exception ignore) {
            return null;
        }
        finally {
            buf.release();
        }
    }

    private Chunk readChunkHeaderOptionally(long block, int expectedId) {
        Chunk chunk = readChunkHeaderOptionally(block);
        return chunk == null || chunk.id != expectedId ? null : chunk;
    }

    /**
     * Compact by moving all chunks next to each other.
     */
    public void compactMoveChunks() {
        compactMoveChunks(100, Long.MAX_VALUE);
    }

    /**
     * Compact the store by moving all chunks next to each other, if there is
     * free space between chunks. This might temporarily increase the file size.
     * Chunks are overwritten irrespective of the current retention time. Before
     * overwriting chunks and before resizing the file, syncFile() is called.
     *
     * @param targetFillRate do nothing if the file store fill rate is higher
     *            than this
     * @param moveSize the number of bytes to move
     */
    private boolean compactMoveChunks(int targetFillRate, long moveSize) {
        boolean result = false;
        storeLock.lock();
        try {
            checkOpen();
            // because serializationExecutor is a single-threaded one and
            // all task submissions to it are done under storeLock,
            // it is guaranteed, that upon after waitAllTasksExecuted()
            // there are no pending / in-progress task here
            if (serializationExecutor != null) {
              serializationExecutor.submit(EmptyRunnable.INSTANCE).get(10, TimeUnit.MINUTES);
            }
            serializationLock.lock();
            try {
                // similarly, all task submissions to bufferSaveExecutor
                // are done under serializationLock, and upon waitAllTasksExecuted()
                // it will be no pending / in-progress task here
                saveChunkLock.lock();
                try {
                    if (lastChunk != null && reuseSpace && getFillRate() <= targetFillRate) {
                        result = compactMoveChunks(moveSize);
                    }
                } finally {
                    saveChunkLock.unlock();
                }
            } finally {
                serializationLock.unlock();
            }
        } catch (MVStoreException e) {
            panic(e);
        } catch (Throwable e) {
            panic(new MVStoreException(MVStoreException.ERROR_INTERNAL, e));
        } finally {
            unlockAndCheckPanicCondition();
        }
        return result;
    }

    private boolean compactMoveChunks(long moveSize) {
        assert storeLock.isHeldByCurrentThread();
        dropUnusedChunks();

        long start = fileStore.getFirstFree() / BLOCK_SIZE;
        int freeBlockCount = fileStore.freeSpace.getFreeBlockCount();
        if (freeBlockCount == 0) {
            return false;
        }

        List<Chunk> chunksToMove = findChunksToMove(start, Math.min(moveSize / BLOCK_SIZE, freeBlockCount));
        if (chunksToMove == null) {
            return false;
        }
        compactMoveChunks(chunksToMove);
        return true;
    }

    private @Nullable List<Chunk> findChunksToMove(long startBlock, long maxBlocksToMove) {
        if (maxBlocksToMove <= 0) {
            return null;
        }

        PriorityQueue<Chunk> queue = new PriorityQueue<>(chunks.size() / 2 + 1, (o1, o2) -> {
            // instead of selection just closest to beginning of the file,
            // pick smaller chunk(s) which sit in between bigger holes
            int res = Integer.compare(o2.collectPriority, o1.collectPriority);
            if (res != 0) {
                return res;
            }
            return Long.signum(o2.block - o1.block);
        });
        long size = 0;
        for (Chunk chunk : chunks.values()) {
            if (chunk.isSaved() && chunk.block > startBlock) {
                chunk.collectPriority = getMovePriority(chunk);
                queue.offer(chunk);
                size += chunk.blockCount;
                while (size > maxBlocksToMove) {
                    Chunk removed = queue.poll();
                    if (removed == null) {
                        break;
                    }
                    size -= removed.blockCount;
                }
            }
        }

        if (queue.isEmpty()) {
            return null;
        }

        List<Chunk> list = new ArrayList<>(queue);
        list.sort(Chunk.PositionComparator.INSTANCE);
        return list;
    }

    private int getMovePriority(Chunk chunk) {
        return fileStore.getMovePriority((int)chunk.block);
    }

    private void compactMoveChunks(@NotNull List<Chunk> move) {
        assert storeLock.isHeldByCurrentThread();
        assert serializationLock.isHeldByCurrentThread();
        assert saveChunkLock.isHeldByCurrentThread();

        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(2 * BLOCK_SIZE, 2 * BLOCK_SIZE);
        try {
            // this will ensure better recognition of the last chunk
            // in case of power failure, since we are going to move older chunks
            // to the end of the file
            writeStoreHeader(buf);
            sync();

            long leftmostBlock = move.get(0).block;
            long originalBlockCount = getAfterLastBlock();
            // we need to ensure that chunks moved within the following loop
            // do not overlap with space just released by chunks moved before them,
            // hence the need to reserve this area [leftmostBlock, originalBlockCount)
            for (Chunk chunk : move) {
                moveChunk(chunk, leftmostBlock, originalBlockCount, buf);
            }

            // update the metadata (hopefully within the file)
            store(leftmostBlock, originalBlockCount);
            sync();

            Chunk chunkToMove = lastChunk;
            assert chunkToMove != null;
            long postEvacuationBlockCount = getAfterLastBlock();

            boolean chunkToMoveIsAlreadyInside = chunkToMove.block < leftmostBlock;
            boolean movedToEOF = !chunkToMoveIsAlreadyInside;
            // move all chunks, which previously did not fit before reserved area
            // now we can re-use previously reserved area [leftmostBlock, originalBlockCount),
            // but need to reserve [originalBlockCount, postEvacuationBlockCount)
            for (Chunk c : move) {
                if (c.block >= originalBlockCount &&
                    moveChunk(c, originalBlockCount, postEvacuationBlockCount, buf)) {
                    assert c.block < originalBlockCount;
                    movedToEOF = true;
                }
            }
            assert postEvacuationBlockCount >= getAfterLastBlock();

            if (movedToEOF) {
                boolean moved = moveChunkInside(chunkToMove, originalBlockCount, buf);

                // store a new chunk with updated metadata (hopefully within a file)
                store(originalBlockCount, postEvacuationBlockCount);
                sync();
                // if chunkToMove did not fit within originalBlockCount (move is
                // false), and since now previously reserved area
                // [originalBlockCount, postEvacuationBlockCount) also can be
                // used, lets try to move that chunk into this area, closer to
                // the beginning of the file
                long lastBoundary = moved || chunkToMoveIsAlreadyInside ?
                                    postEvacuationBlockCount : chunkToMove.block;
                moved = !moved && moveChunkInside(chunkToMove, lastBoundary, buf);
                if (moveChunkInside(lastChunk, lastBoundary, buf) || moved) {
                    store(lastBoundary, -1);
                }
            }
        } finally {
            buf.release();
        }

        shrinkFileIfPossible(0);
        sync();
    }

    private void store(long reservedLow, long reservedHigh) {
        saveChunkLock.unlock();
        try {
            serializationLock.unlock();
            try {
                storeNow(true, reservedLow, () -> reservedHigh);
            } finally {
                serializationLock.lock();
            }
        } finally {
            saveChunkLock.lock();
        }
    }

    private boolean moveChunkInside(Chunk chunkToMove, long boundary, ByteBuf chunkBuffer) {
        boolean res = chunkToMove.block >= boundary &&
                      fileStore.predictAllocation(chunkToMove.blockCount, boundary, -1) < boundary &&
                      moveChunk(chunkToMove, boundary, -1, chunkBuffer);
        assert !res || chunkToMove.block + chunkToMove.blockCount <= boundary;
        return res;
    }

    /**
     * Move specified chunk into free area of the file. "Reserved" area
     * specifies file interval to be avoided, when un-allocated space will be
     * chosen for a new chunk's location.
     *
     * @param chunk to move
     * @param reservedAreaLow low boundary of reserved area, inclusive
     * @param reservedAreaHigh high boundary of reserved area, exclusive
     * @return true if block was moved, false otherwise
     */
    private boolean moveChunk(Chunk chunk, long reservedAreaLow, long reservedAreaHigh, ByteBuf chunkBuffer) {
        // ignore if already removed during the previous store operations
        // those are possible either as explicit commit calls
        // or from meta map updates at the end of this method
        if (!chunks.containsKey(chunk.id)) {
            return false;
        }

        long start = chunk.block * BLOCK_SIZE;
        int length = chunk.blockCount * BLOCK_SIZE;
        long block;

        long newPosition = fileStore.allocate(length, reservedAreaLow, reservedAreaHigh);
        // copy old content first and then overwrite chunk header and footer
        fileStore.copy(start, newPosition, length);

        chunkBuffer.clear();
        fileStore.readFully(chunkBuffer, start, Chunk.HEADER_LENGTH);
        Chunk chunkFromFile = Chunk.readChunkHeader(chunkBuffer, start);

        block = newPosition / BLOCK_SIZE;
        // in the absence of a reserved area,
        // block should always move closer to the beginning of the file
        assert reservedAreaHigh > 0 || block <= chunk.block : block + " " + chunk;
        // can not set chunk's new block/len until it's fully written at new location,
        // because concurrent reader can pick it up prematurely,
        // also occupancy accounting fields should not leak into header
        chunkFromFile.block = block;
        chunkFromFile.next = 0;
        chunkBuffer.clear();
        chunkFromFile.writeHeader(chunkBuffer);
        fileStore.writeFully(chunkBuffer, newPosition);

        chunkBuffer.clear();
        chunkFromFile.writeFooter(chunkBuffer);
        fileStore.writeFully(chunkBuffer, (newPosition + length) - Chunk.FOOTER_LENGTH);

        fileStore.free(start, length);
        chunk.block = block;
        chunk.next = 0;
        putChunkMetadata(chunk.id, chunk);
        return true;
    }

    /**
     * Force all stored changes to be written to the storage. The default
     * implementation calls FileChannel.force(true).
     */
    public void sync() {
        checkOpen();
        FileStore f = fileStore;
        if (f != null) {
            f.sync();
        }
    }

    /**
     * Compact store file, that is, compact blocks that have a low
     * fill rate, and move chunks next to each other. This will typically
     * shrink the file. Changes are flushed to the file, and old
     * chunks are overwritten.
     *
     * @param maxCompactTime the maximum time in milliseconds to compact
     */
    public void compactFile(int maxCompactTime) {
        setRetentionTime(0);
        long stopAt = System.nanoTime() + maxCompactTime * 1_000_000L;
        while (compact(95, 16 * 1024 * 1024)) {
            sync();
            compactMoveChunks(95, 16 * 1024 * 1024);
            if (System.nanoTime() - stopAt > 0L) {
                break;
            }
        }
    }

    /**
     * Try to increase the fill rate by re-writing partially full chunks. Chunks
     * with a low number of live items are re-written.
     * <p>
     * If the current fill rate is higher than the target fill rate, nothing is
     * done.
     * <p>
     * Please note this method will not necessarily reduce the file size, as
     * empty chunks are not overwritten.
     * <p>
     * Only data of open maps can be moved. For maps that are not open, the old
     * chunk is still referenced. Therefore, it is recommended to open all maps
     * before calling this method.
     *
     * @param targetFillRate the minimum percentage of live entries
     * @param write the minimum number of bytes to write
     * @return if a chunk was re-written
     */
    public boolean compact(int targetFillRate, int write) {
        if (!reuseSpace || lastChunk == null) {
            return false;
        }

        checkOpen();
        if (targetFillRate > 0 && getChunksFillRate() < targetFillRate) {
            // We can't wait forever for the lock here,
            // because if called from the background thread,
            // it might go into deadlock with concurrent database closure
            // and attempt to stop this thread.
            try {
                if (storeLock.tryLock(10, TimeUnit.MILLISECONDS)) {
                    try {
                        return rewriteChunks(write, 100);
                    } finally {
                        storeLock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private boolean rewriteChunks(int writeLimit, int targetFillRate) {
        serializationLock.lock();
        try {
            TxCounter txCounter = registerVersionUsage();
            try {
                acceptChunkOccupancyChanges(getTimeSinceCreation(), currentVersion);
                Queue<Chunk> old = findOldChunks(writeLimit, targetFillRate);
                int oldSize = old.size();
                if (oldSize != 0) {
                    IntSet idSet = new IntOpenHashSet(oldSize);
                    for (Chunk c : old) {
                        idSet.add(c.id);
                    }
                    return compactRewrite(idSet) > 0;
                }
            } finally {
                deregisterVersionUsage(txCounter);
            }
            return false;
        } finally {
            serializationLock.unlock();
        }
    }

    /**
     * Get the current fill rate (percentage of used space in the file). Unlike
     * the fill rate of the store, here we only account for chunk data; the fill
     * rate here is how much of the chunk data is live (still referenced). Young
     * chunks are considered live.
     *
     * @return the fill rate, in percent (100 is completely full)
     */
    public int getChunksFillRate() {
        return getChunksFillRate(true);
    }

    public int getRewritableChunksFillRate() {
        return getChunksFillRate(false);
    }

    private int getChunksFillRate(boolean all) {
        long maxLengthSum = 1;
        long maxLengthLiveSum = 1;
        long time = getTimeSinceCreation();
        for (Chunk c : chunks.values()) {
            if (all || isRewritable(c, time)) {
                assert c.maxLen >= 0;
                maxLengthSum += c.maxLen;
                maxLengthLiveSum += c.maxLenLive;
            }
        }
        // the fill rate of all chunks combined
        return (int) (100 * maxLengthLiveSum / maxLengthSum);
    }

    /**
     * Get data chunks count.
     *
     * @return number of existing chunks in store.
     */
    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * Get data pages count.
     *
     * @return number of existing pages in store.
     */
    public int getPageCount() {
        int count = 0;
        for (Chunk chunk : chunks.values()) {
            count += chunk.pageCount;
        }
        return count;
    }

    /**
     * Get live data pages count.
     *
     * @return number of existing live pages in store.
     */
    public int getLivePageCount() {
        int count = 0;
        for (Chunk chunk : chunks.values()) {
            count += chunk.getLivePageCount();
        }
        return count;
    }

    private int getProjectedFillRate(@SuppressWarnings("SameParameterValue") int thresholdChunkFillRate) {
        saveChunkLock.lock();
        try {
            int vacatedBlocks = 0;
            long maxLengthSum = 1;
            long maxLengthLiveSum = 1;
            long time = getTimeSinceCreation();
            for (Chunk c : chunks.values()) {
                assert c.maxLen >= 0;
                if (isRewritable(c, time) && c.getFillRate() <= thresholdChunkFillRate) {
                    assert c.maxLen >= c.maxLenLive;
                    vacatedBlocks += c.blockCount;
                    maxLengthSum += c.maxLen;
                    maxLengthLiveSum += c.maxLenLive;
                }
            }
            int additionalBlocks = (int) (vacatedBlocks * maxLengthLiveSum / maxLengthSum);
            return fileStore.getProjectedFillRate(vacatedBlocks - additionalBlocks);
        } finally {
            saveChunkLock.unlock();
        }
    }

    public int getFillRate() {
        saveChunkLock.lock();
        try {
            return fileStore.getFillRate();
        } finally {
            saveChunkLock.unlock();
        }
    }

    private @NotNull Queue<Chunk> findOldChunks(int writeLimit, int targetFillRate) {
        assert lastChunk != null;
        long time = getTimeSinceCreation();

        // the queue will contain chunks we want to free up
        // the smaller the collectionPriority, the more desirable this chunk's re-write is
        // queue will be ordered in descending order of collectionPriority values,
        // so most desirable chunks will stay at the tail
        PriorityQueue<Chunk> queue = new PriorityQueue<>(this.chunks.size() / 4 + 1,
                (o1, o2) -> {
                    int comp = Integer.compare(o2.collectPriority, o1.collectPriority);
                    if (comp == 0) {
                        comp = Long.compare(o2.maxLenLive, o1.maxLenLive);
                    }
                    return comp;
                });

        long totalSize = 0;
        long latestVersion = lastChunk.version + 1;
        for (Chunk chunk : chunks.values()) {
            // only look at chunk older than the retention time
            // (it's possible to compact chunks earlier, but right
            // now we don't do that)
            int fillRate = chunk.getFillRate();
            if (isRewritable(chunk, time) && fillRate <= targetFillRate) {
                long age = Math.max(1, latestVersion - chunk.version);
                chunk.collectPriority = (int) (fillRate * 1000 / age);
                totalSize += chunk.maxLenLive;
                queue.offer(chunk);
                while (totalSize > writeLimit) {
                    Chunk removed = queue.poll();
                    if (removed == null) {
                        break;
                    }
                    totalSize -= removed.maxLenLive;
                }
            }
        }

        return queue;
    }

    private boolean isRewritable(Chunk chunk, long time) {
        return chunk.isRewritable() && isSeasonedChunk(chunk, time);
    }

    private int compactRewrite(IntSet set) {
        assert storeLock.isHeldByCurrentThread();
        assert currentStoreVersion < 0; // we should be able to do tryCommit() -> store()
        acceptChunkOccupancyChanges(getTimeSinceCreation(), currentVersion);
        int rewrittenPageCount = rewriteChunks(set, false);
        acceptChunkOccupancyChanges(getTimeSinceCreation(), currentVersion);
        rewrittenPageCount += rewriteChunks(set, true);
        return rewrittenPageCount;
    }

    private int rewriteChunks(IntSet set, boolean secondPass) {
        int rewrittenPageCount = 0;
        for (IntIterator iterator = set.iterator(); iterator.hasNext(); ) {
            int chunkId = iterator.nextInt();
            Chunk chunk = chunks.get(chunkId);
            LongArrayList toc = getToC(chunk);
            for (int pageNo = 0; pageNo < chunk.pageCount && chunk.isLivePage(pageNo); pageNo++) {
                long tocElement = toc.getLong(pageNo);
                int mapId = DataUtil.getPageMapId(tocElement);
                MVMap<?, ?> map = null;
                if (mapId < MIN_USER_MAP_ID) {
                    for (MVMap<?, ?> metaMap : metaMaps) {
                        if (mapId == metaMap.getId()) {
                            map = metaMap;
                            break;
                        }
                    }
                }
                if (map == null) {
                    map = getMap(mapId);
                }
                if (map != null && !map.isClosed()) {
                    assert !map.isSingleWriter();
                    if (!secondPass && !DataUtil.isLeafPage(tocElement)) {
                        continue;
                    }

                    long pagePos = DataUtil.getPageInfo(chunkId, tocElement);
                    serializationLock.unlock();
                    try {
                        if (map.rewritePage(pagePos)) {
                            ++rewrittenPageCount;
                            if (map == mapNameToMetadata) {
                                markMetaChanged();
                            }
                        }
                    }
                    finally {
                        serializationLock.lock();
                    }
                }
            }
        }
        return rewrittenPageCount;
    }

    /**
     * Read a page.
     *
     * @param map the map
     * @param pageInfo the page position
     * @return the page
     */
    <K,V> Page<K,V> readPage(MVMap<K,V> map, long pageInfo) {
        try {
            if (!DataUtil.isPageSaved(pageInfo)) {
                throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "Page is not saved yet");
            }

            Cache<@NotNull Long, @NotNull Page<?, ?>> cache = getPageCache(DataUtil.isLeafPage(pageInfo));
            Chunk chunk = getChunk(DataUtil.getPageChunkId(pageInfo));
            if (cache == null) {
                return doReadPage(map, pageInfo, chunk);
            }
            else {
                //noinspection unchecked
                return (Page<K, V>)cache.get(pageInfo, info -> doReadPage(map, info, chunk));
            }
        } catch (MVStoreException e) {
            if (config.recoveryMode) {
                return map.createEmptyLeaf();
            }
            throw e;
        }
    }

    private @NotNull <K, V> Page<K, V> doReadPage(@NotNull MVMap<K, V> map, long pageInfo, @NotNull Chunk chunk) {
        int pageOffset = DataUtil.getPageOffset(pageInfo);
        try {
            Page<K, V> page;
            ByteBuf buf = chunk.readBufferForPage(fileStore, pageOffset, pageInfo);
            try {
                page = DataUtil.isLeafPage(pageInfo) ? new LeafPage<>(map, buf, pageInfo, chunk.id) : new NonLeafPage<>(map, buf, pageInfo, chunk.id);
            }
            finally {
                buf.release();
            }
            assert page.pageNo >= 0;
            return page;
        } catch (MVStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT,
                                       "Unable to read the page (info=" + pageInfo +
                                       ", chunk=" + chunk.id + ", offset=" + pageOffset + ")", e);
        }
    }

    private @NotNull LongArrayList getToC(Chunk chunk) {
        assert chunk.tocPos != 0;
        LongArrayList toc = chunkIdToToC.get(chunk.id, __ -> chunk.readToC(fileStore));
        assert toc.size() == chunk.pageCount : toc.size() + " != " + chunk.pageCount;
        return toc;
    }

    /**
     * Remove a page.
     *  @param pos the position of the page
     * @param version at which page was removed
     * @param pinned whether page is considered pinned
     * @param pageNo sequential page number within chunk
     */
    void accountForRemovedPage(long pos, long version, boolean pinned, int pageNo) {
        assert DataUtil.isPageSaved(pos);
        if (pageNo < 0) {
            pageNo = calculatePageNo(pos);
        }
        removedPages.add(new RemovedPageInfo(pos, pinned, version, pageNo));
    }

    private int calculatePageNo(long pageInfo) {
        int pageNo = -1;
        Chunk chunk = getChunk(DataUtil.getPageChunkId(pageInfo));
        LongArrayList toC = getToC(chunk);
        long[] longs = toC.elements();
        int offset = DataUtil.getPageOffset(pageInfo);
        int low = 0;
        int high = toC.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = DataUtil.getPageOffset(longs[mid]);
            if (midVal < offset) {
                low = mid + 1;
            } else if (midVal > offset) {
                high = mid - 1;
            } else {
                pageNo = mid;
                break;
            }
        }
        return pageNo;
    }

    @NotNull LZ4Compressor getCompressor() {
        LZ4Compressor result = compressor;
        if (result != null) {
            return result;
        }

        if (config.compress == 1) {
            result = LZ4Factory.fastestJavaInstance().fastCompressor();
        }
        else {
            result = LZ4Factory.fastestJavaInstance().highCompressor(Math.max(9, config.compress));
        }
        compressor = result;
        return result;
    }

    @NotNull LZ4FastDecompressor getDecompressor() {
        LZ4FastDecompressor result = decompressor;
        if (result == null) {
            result = LZ4Factory.fastestJavaInstance().fastDecompressor();
            decompressor = result;
        }
        return result;
    }

    int getCompressionLevel() {
        return config.compress;
    }

    public int getKeysPerPage() {
        return config.keysPerPage;
    }

    public boolean getReuseSpace() {
        return reuseSpace;
    }

    /**
     * Whether empty space in the file should be re-used. If enabled, old data
     * is overwritten (default). If disabled, writes are appended at the end of
     * the file.
     * <p>
     * This setting is specially useful for online backup. To create an online
     * backup, disable this setting, then copy the file (starting at the
     * beginning of the file). In this case, concurrent backup and write
     * operations are possible (obviously the backup process needs to be faster
     * than the write operations).
     *
     * @param reuseSpace the new value
     */
    public void setReuseSpace(boolean reuseSpace) {
        this.reuseSpace = reuseSpace;
    }

    public int getRetentionTime() {
        return retentionTime;
    }

    /**
     * How long to retain old, persisted chunks, in milliseconds. Chunks that
     * are older may be overwritten once they contain no live data.
     * <p>
     * The default value is 45000 (45 seconds) when using the default file
     * store. It is assumed that a file system and hard disk will flush all
     * write buffers within this time. Using a lower value might be dangerous,
     * unless the file system and hard disk flush the buffers earlier. To
     * manually flush the buffers, use
     * <code>MVStore.getFile().force(true)</code>, however please note that
     * according to various tests this does not always work as expected
     * depending on the operating system and hardware.
     * <p>
     * The retention time needs to be long enough to allow reading old chunks
     * while traversing over the entries of a map.
     * <p>
     * This setting is not persisted.
     *
     * @param ms how many milliseconds to retain old chunks (0 to overwrite them
     *            as early as possible)
     */
    public void setRetentionTime(int ms) {
        this.retentionTime = ms;
    }

    /**
     * Get the oldest version to retain.
     * We keep at least number of previous versions specified by "versionsToKeep"
     * configuration parameter (default 0).
     * Oldest version determination also takes into account calls (de)registerVersionUsage(),
     * an will not release the version, while version is still in use.
     *
     * @return the version
     */
    long getOldestVersionToKeep() {
        long v = oldestVersionToKeep.get();
        v = Math.max(v - config.versionsToKeep, INITIAL_VERSION);
        if (fileStore != null) {
            long storeVersion = lastChunkVersion() - 1;
            if (storeVersion != INITIAL_VERSION && storeVersion < v) {
                v = storeVersion;
            }
        }
        return v;
    }

    private void setOldestVersionToKeep(long oldestVersionToKeep) {
        boolean success;
        do {
            long current = this.oldestVersionToKeep.get();
            // Oldest version may only advance, never goes back
            success = oldestVersionToKeep <= current ||
                        this.oldestVersionToKeep.compareAndSet(current, oldestVersionToKeep);
        } while (!success);
    }

    private long lastChunkVersion() {
        Chunk chunk = lastChunk;
        return chunk == null ? INITIAL_VERSION + 1 : chunk.version;
    }

    /**
     * Check whether all data can be read from this version. This requires that
     * all chunks referenced by this version are still available (not
     * overwritten).
     *
     * @param version the version
     * @return true if all data can be read
     */
    private boolean isKnownVersion(long version) {
        if (version > currentVersion || version < 0) {
            return false;
        }
        if (version == currentVersion || chunks.isEmpty()) {
            // no stored data
            return true;
        }

        // need to check if a chunk for this version exists
        Chunk newestChunkForVersion = null;
        for (Chunk chunkCandidate : chunks.values()) {
            if (chunkCandidate.version <= version) {
                if (newestChunkForVersion == null || chunkCandidate.id > newestChunkForVersion.id) {
                    newestChunkForVersion = chunkCandidate;
                }
            }
        }
        if (newestChunkForVersion == null) {
            return false;
        }

        // also, all chunks referenced by this version
        // need to be available in the file
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(Chunk.MAX_METADATA_LENGTH, Chunk.MAX_METADATA_LENGTH);
        try {
            newestChunkForVersion = readChunkHeader(newestChunkForVersion.block, buf);
            MVMap<Integer, byte[]> oldChunkMap = chunkIdToChunkMetadata.openReadOnly(newestChunkForVersion.chunkMapRootPageInfo, version);
            for (Iterator<Integer> it = oldChunkMap.keyIterator(null); it.hasNext(); ) {
                Integer chunkKey = it.next();
                if (!chunkIdToChunkMetadata.containsKey(chunkKey)) {
                    byte[] metadata = oldChunkMap.get(chunkKey);
                    Chunk chunk = Chunk.readMetadata(chunkKey, Unpooled.wrappedBuffer(metadata));
                    buf.clear();
                    Chunk test = readChunkHeaderAndFooter(chunk.block, chunk.id, buf);
                    if (test == null) {
                        return false;
                    }
                }
            }
        }
        catch (MVStoreException ignore) {
            // the chunk missing where the metadata is stored
            return false;
        }
        finally {
            buf.release();
        }
        return true;
    }

    /**
     * Adjust amount of "unsaved memory" meaning amount of RAM occupied by pages
     * not saved yet to the file. This is the amount which triggers auto-commit.
     *
     * @param memory adjustment
     */
    public void registerUnsavedMemory(int memory) {
        // this counter was intentionally left unprotected against race
        // condition for performance reasons
        // TODO: evaluate performance impact of atomic implementation,
        //       since updates to unsavedMemory are largely aggregated now
        unsavedMemory += memory;
        if (config.autoCommitBufferSize > 0 && unsavedMemory > config.autoCommitBufferSize) {
            saveNeeded = true;
        }
    }

    boolean isSaveNeeded() {
        return saveNeeded;
    }

    /**
     * This method is called before writing to a map.
     *
     * @param map the map
     */
    void beforeWrite(MVMap<?, ?> map) {
        if (!saveNeeded || fileStore == null || !isOpenOrStopping()) {
            return;
        }

        // to avoid infinite recursion via store() -> dropUnusedChunks() -> chunkIdToChunkMetadata.remove()
        if (map == chunkIdToChunkMetadata) {
            return;
        }

        // condition below is to prevent potential deadlock,
        // because we should never seek storeLock while holding
        // map root lock
        if (!storeLock.isHeldByCurrentThread() && map.getRoot().isLockedByCurrentThread()) {
            return;
        }

        // check again, because it could have been written by now
        if (config.autoCommitBufferSize > 0 && needStore()) {
            // serialization is already in progress - do not queue yet another
            if (serializationExecutor != null && serializationExecutor.getActiveCount() > 0) {
                return;
            }

            saveNeeded = false;

            // if unsaved memory creation rate is to high,
            // some back pressure need to be applied
            // to slow things down and avoid OOME
            if (!map.isSingleWriter() && requireStore()) {
                if (serializationExecutor == null) {
                    commit(MVStore::requireStore);
                }
                else {
                    tryCommit(MVStore::needStore);
                }
            }
            else {
                tryCommit(MVStore::needStore);
            }
        }
    }

    private boolean requireStore() {
        return 3 * unsavedMemory > 4 * config.autoCommitBufferSize;
    }

    private boolean needStore() {
        return unsavedMemory > config.autoCommitBufferSize;
    }

    /**
     * Revert to the beginning of the current version, reverting all uncommitted
     * changes.
     */
    public void rollback() {
        rollbackTo(currentVersion);
    }

    /**
     * Revert to the beginning of the given version. All later changes (stored
     * or not) are forgotten. All maps that were created later are closed. A
     * rollback to a version before the last stored version is immediately
     * persisted. Rollback to version 0 means all data is removed.
     *
     * @param version the version to revert to
     */
    public void rollbackTo(long version) {
        storeLock.lock();
        try {
            checkOpen();
            if (version == 0) {
                // special case: remove all data
                //noinspection rawtypes
                for (MVMap map : metaMaps) {
                    //noinspection unchecked
                    map.setInitialRoot(map.createEmptyLeaf(), INITIAL_VERSION);
                }
                deadChunks.clear();
                removedPages.clear();
                chunks.clear();
                clearCaches();
                if (fileStore != null) {
                    saveChunkLock.lock();
                    try {
                        fileStore.clear();
                    } finally {
                        saveChunkLock.unlock();
                    }
                }
                lastChunk = null;
                versions.clear();
                currentVersion = version;
                setWriteVersion(version);
                metaChanged = false;
                for (MVMap<?, ?> m : maps.values()) {
                    m.close();
                }
                return;
            }
            if (!isKnownVersion(version)) {
                throw new IllegalArgumentException("Unknown version " + version);
            }

            TxCounter txCounter;
            while ((txCounter = versions.peekLast()) != null && txCounter.version >= version) {
                versions.removeLast();
            }
            currentTxCounter = new TxCounter(version);

            for (MVMap<?, ?> map : metaMaps) {
                map.rollbackTo(version);
            }
            metaChanged = false;
            // find out which chunks to remove,
            // and which is the newest chunk to keep
            // (the chunk list can have gaps)
            IntList remove = new IntArrayList();
            Chunk keep = null;
            serializationLock.lock();
            try {
                for (Chunk c : chunks.values()) {
                    if (c.version > version) {
                        remove.add(c.id);
                    } else if (keep == null || keep.version < c.version) {
                        keep = c;
                    }
                }
                if (!remove.isEmpty()) {
                    // remove the youngest first, so we don't create gaps
                    // (in case we remove many chunks)
                    remove.sort(IntComparators.OPPOSITE_COMPARATOR);
                    saveChunkLock.lock();
                    ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(2 * BLOCK_SIZE);
                    try {
                        for (int id : remove) {
                            Chunk c = chunks.remove(id);
                            if (c != null) {
                                long start = c.block * BLOCK_SIZE;
                                int length = c.blockCount * BLOCK_SIZE;
                                freeFileSpace(start, length);
                                // overwrite the chunk, so it is not be used later on
                                buf.clear();
                                buf.ensureWritable(length);
                                buf.setZero(0, length);
                                buf.setIndex(0, length);
                                write(start, buf);
                                // only really needed if we remove many chunks, when writes are
                                // re-ordered - but we do it always, because rollback is not
                                // performance critical
                                sync();
                            }
                        }
                        lastChunk = keep;
                        writeStoreHeader(buf);
                        buf.clear();
                        readStoreHeader(buf);
                    } finally {
                        try {
                            saveChunkLock.unlock();
                        } finally {
                            buf.release();
                        }
                    }
                }
            } finally {
                serializationLock.unlock();
            }
            deadChunks.clear();
            removedPages.clear();
            clearCaches();
            currentVersion = version;
            onVersionChange(currentVersion);
            for (MVMap<?, ?> m : new ArrayList<>(maps.values())) {
                int id = m.getId();
                if (m.getCreateVersion() >= version) {
                    m.close();
                    maps.remove(id);
                } else {
                    if (!m.rollbackRoot(version)) {
                        m.setRootPageInfo(getRootPageInfo(id), version - 1);
                    }
                }
            }
            assert !hasUnsavedChanges();
        } finally {
            unlockAndCheckPanicCondition();
        }
    }

    private void clearCaches() {
        if (nonLeafPageCache != null) {
            nonLeafPageCache.invalidateAll();
            leafPageCache.invalidateAll();
            chunkIdToToC.invalidateAll();
        }
    }

    private long getRootPageInfo(int mapId) {
        Long root = layout.get(mapId);
        return root == null ? 0 : root;
    }

    /**
     * Get the current version of the data. When a new store is created, the
     * version is 0.
     *
     * @return the version
     */
    public long getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Get the file store.
     *
     * @return the file store
     */
    public FileStore getFileStore() {
        return fileStore;
    }

    /**
     * Get the store header. This data is for informational purposes only. The
     * data is subject to change in future versions. The data should not be
     * modified (doing so may corrupt the store).
     *
     * @return the store header
     */
    public StoreHeader getStoreHeader() {
        return storeHeader;
    }

    private void checkOpen() {
        if (!isOpenOrStopping()) {
            throw new MVStoreException(MVStoreException.ERROR_CLOSED, "This store is closed", panicException);
        }
    }

    /**
     * Rename a map.
     *
     * @param map the map
     * @param _newName the new name
     */
    public void renameMap(@NotNull MVMap<?, ?> map, @NotNull CharSequence _newName) {
        checkOpen();

        int id = map.getId();
        if (id < MIN_USER_MAP_ID) {
            throw new IllegalArgumentException("Renaming the meta map is not allowed");
        }

        AsciiString asciiName = AsciiString.of(_newName);
        if (mapNameToMetadata.containsKey(asciiName)) {
            throw new IllegalArgumentException("A map named " + asciiName + " already exists");
        }

        // at first create a new name as an "alias"
        MapMetadata existingMetadata = mapNameToMetadata.putIfAbsent(asciiName, new MapMetadata(id, map.getCreateVersion()));
        if (existingMetadata != null) {
            if (existingMetadata.id != id) {
                throw new IllegalArgumentException("A map named " + asciiName + " already exists");
            }

            // cope with the case of previously unfinished rename
            Set<AsciiString> keysToRemove = null;
            Cursor<AsciiString, MapMetadata> cursor = mapNameToMetadata.cursor(null);
            while (cursor.hasNext()) {
                AsciiString oldName = cursor.next();
                MapMetadata metadata = cursor.getValue();
                if (metadata.id == id && !oldName.equals(asciiName)) {
                    if (keysToRemove == null) {
                        keysToRemove = new HashSet<>();
                    }
                    keysToRemove.add(oldName);
                }
            }

            if (keysToRemove != null) {
                for (AsciiString key : keysToRemove) {
                    mapNameToMetadata.remove(key);
                    markMetaChanged();
                }
            }
            return;
        }

        // switch roles of a new and old names - old one is an alias now
        // get rid of the old name completely
        Cursor<AsciiString, MapMetadata> cursor = mapNameToMetadata.cursor(null);
        while (cursor.hasNext()) {
            AsciiString oldName = cursor.next();
            MapMetadata metadata = cursor.getValue();
            if (metadata.id == id) {
                if (!oldName.equals(asciiName)) {
                    mapNameToMetadata.remove(oldName);
                }
                break;
            }
        }
        markMetaChanged();
    }

    /**
     * Remove a map from the current version of the store.
     *
     * @param map the map to remove
     */
    public void removeMap(MVMap<?,?> map) {
        storeLock.lock();
        try {
            checkOpen();
            if (map.getId() < MIN_USER_MAP_ID) {
                throw new IllegalArgumentException("Removing the meta map is not allowed");
            }
            RootReference<?,?> rootReference = map.clearIt();
            map.close();

            updateCounter += rootReference.updateCounter;
            updateAttemptCounter += rootReference.updateAttemptCounter;

            AsciiString name = (AsciiString)getMapName(map.getId());
            if (mapNameToMetadata.remove(name) != null) {
                markMetaChanged();
            }
        } finally {
            storeLock.unlock();
        }
    }

    /**
     * Performs final stage of map removal - delete root location info from the layout table.
     * Map is supposedly closed and anonymous and has no outstanding usage by now.
     *
     * @param mapId to deregister
     */
    void deregisterMapRoot(int mapId) {
        if (layout.remove(mapId) != null) {
            markMetaChanged();
        }
    }

    /**
     * Get the name of the given map.
     *
     * @param id the map id
     * @return the name, or null if not found
     */
    public @Nullable CharSequence getMapName(int id) {
        Cursor<AsciiString, MapMetadata> cursor = mapNameToMetadata.cursor(null);
        while (cursor.hasNext()) {
            AsciiString name = cursor.next();
            MapMetadata metadata = cursor.getValue();
            if (metadata.id == id) {
                return name;
            }
        }
        return null;
    }

    public void triggerAutoSave() {
        triggerAutoSave(false);
    }
    /**
     * Commit and save all changes, if there are any, and compact the store if
     * needed. Some part of work is executed asynchronously.
     */
    public void triggerAutoSave(boolean force) {
        try {
            if (!isOpenOrStopping() || isReadOnly()) {
                return;
            }

            if (!force && (getTimeSinceCreation() <= (lastCommitTime + autoCommitDelay))) {
                return;
            }

            tryCommit(null);

            int autoCompactFillRate = config.autoCompactFillRate;
            if (autoCompactFillRate == 0) {
                return;
            }

            int fillRate = getFillRate();
            if (fillRate >= autoCompactFillRate && lastChunk != null) {
                int chunksFillRate = getRewritableChunksFillRate();
                chunksFillRate = isIdle() ? 100 - (100 - chunksFillRate) / 2 : chunksFillRate;
                if (chunksFillRate < getTargetFillRate()) {
                    if (storeLock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            int writeLimit = config.autoCommitBufferSize * fillRate / Math.max(chunksFillRate, 1);
                            if (!isIdle()) {
                                writeLimit /= 4;
                            }
                            if (rewriteChunks(writeLimit, chunksFillRate)) {
                                dropUnusedChunks();
                            }
                        }
                        finally {
                            storeLock.unlock();
                        }
                    }
                }
            }
            autoCompactLastFileOpCount = fileStore.getWriteCount() + fileStore.getReadCount();
        } catch (InterruptedException ignore) {
        } catch (Throwable e) {
            handleException(e);
            if (config.backgroundExceptionHandler == null) {
                throw e;
            }
        }
    }

    private void doMaintenance(int targetFillRate) {
        if (targetFillRate <= 0 || lastChunk == null || !reuseSpace) {
            return;
        }

        try {
            int lastProjectedFillRate = -1;
            for (int cnt = 0; cnt < 5; cnt++) {
                int fillRate = getFillRate();
                int projectedFillRate = fillRate;
                if (fillRate > targetFillRate) {
                    projectedFillRate = getProjectedFillRate(100);
                    if (projectedFillRate > targetFillRate || projectedFillRate <= lastProjectedFillRate) {
                        break;
                    }
                }
                lastProjectedFillRate = projectedFillRate;
                // We can't wait forever for the lock here,
                // because if called from the background thread,
                // it might go into deadlock with concurrent database closure
                // and attempt to stop this thread.
                if (!storeLock.tryLock(10, TimeUnit.MILLISECONDS)) {
                    break;
                }
                try {
                    int writeLimit = config.autoCommitBufferSize * targetFillRate / Math.max(projectedFillRate, 1);
                    if (projectedFillRate < fillRate) {
                        if ((!rewriteChunks(writeLimit, targetFillRate) || dropUnusedChunks() == 0) && cnt > 0) {
                            break;
                        }
                    }
                    if (!compactMoveChunks(101, writeLimit)) {
                        break;
                    }
                } finally {
                    unlockAndCheckPanicCondition();
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int getTargetFillRate() {
        int targetRate = config.autoCompactFillRate;
        // use a lower fill rate if there were any file operations since the last time
        if (!isIdle()) {
            targetRate /= 2;
        }
        return targetRate;
    }

    private boolean isIdle() {
        return autoCompactLastFileOpCount == fileStore.getWriteCount() + fileStore.getReadCount();
    }

    private void handleException(Throwable error) {
        if (config.backgroundExceptionHandler != null) {
            try {
                config.backgroundExceptionHandler.accept(error, this);
            } catch(Throwable e) {
                if (error != e) { // OOME may be the same
                    error.addSuppressed(e);
                }
            }
        }
    }

    private boolean isOpen() {
        return state == STATE_OPEN;
    }

    /**
     * Determine that store is open, or wait for it to be closed (by other thread)
     * @return true if store is open, false otherwise
     */
    public boolean isClosed() {
        if (isOpen()) {
            return false;
        }
        storeLock.lock();
        try {
            assert state == STATE_CLOSED;
            return true;
        } finally {
            storeLock.unlock();
        }
    }

    private boolean isOpenOrStopping() {
        return state <= STATE_STOPPING;
    }

    /**
     * Get the auto-commit delay.
     *
     * @return the delay in milliseconds, or 0 if auto-commit is disabled.
     */
    public int getAutoCommitDelay() {
        return autoCommitDelay;
    }

    /**
     * Get the maximum memory (in bytes) used for unsaved pages. If this number
     * is exceeded, unsaved changes are stored to disk.
     *
     * @return the memory in bytes
     */
    public int getAutoCommitMemory() {
        return config.autoCommitBufferSize;
    }

    /**
     * Get the estimated memory (in bytes) of unsaved data. If the value exceeds
     * the auto-commit memory, the changes are committed.
     * <p>
     * The returned value is an estimation only.
     *
     * @return the memory in bytes
     */
    public int getUnsavedMemory() {
        return unsavedMemory;
    }

    /**
     * Put the page in the cache.
     * @param page the page
     */
    void cachePage(Page<?,?> page) {
        Cache<Long, Page<?, ?>> cache = getPageCache(page.isLeaf());
        if (cache != null) {
            cache.put(page.getPosition(), page);
        }
    }

    /**
     * Get the cache.
     *
     * @return the cache
     */
    public CacheStats getCacheStats(boolean nonLeaf) {
        return nonLeaf ? nonLeafPageCache.stats() : leafPageCache.stats();
    }

    /**
     * Whether the store is read-only.
     *
     * @return true if it is
     */
    public boolean isReadOnly() {
        return fileStore != null && fileStore.isReadOnly();
    }

    public int getLeafRatio() {
        return (int)(leafCount * 100 / Math.max(1, leafCount + nonLeafCount));
    }

    public double getUpdateFailureRatio() {
        long updateCounter = this.updateCounter;
        long updateAttemptCounter = this.updateAttemptCounter;

        for (MVMap<?, ?> map : metaMaps) {
            RootReference<?,?> rootReference = map.getRoot();
            updateCounter += rootReference.updateCounter;
            updateAttemptCounter += rootReference.updateAttemptCounter;
        }

        for (MVMap<?, ?> map : maps.values()) {
            RootReference<?,?> root = map.getRoot();
            updateCounter += root.updateCounter;
            updateAttemptCounter += root.updateAttemptCounter;
        }
        return updateAttemptCounter == 0 ? 0 : 1 - ((double)updateCounter / updateAttemptCounter);
    }

    /**
     * Register opened operation (transaction).
     * This would increment usage counter for the current version.
     * This version (and all after it) should not be dropped until all
     * transactions involved are closed and usage counter goes to zero.
     * @return TxCounter to be decremented when operation finishes (transaction closed).
     */
    public TxCounter registerVersionUsage() {
        TxCounter txCounter;
        while(true) {
            txCounter = currentTxCounter;
            if(txCounter.incrementAndGet() > 0) {
                return txCounter;
            }
            // The only way for counter to be negative
            // if it was retrieved right before onVersionChange()
            // and now onVersionChange() is done.
            // This version is eligible for reclamation now
            // and should not be used here, so restore count
            // not to upset accounting and try again with a new
            // version (currentTxCounter should have changed).
            assert txCounter != currentTxCounter : txCounter;
            txCounter.decrementAndGet();
        }
    }

    /**
     * De-register (close) completed operation (transaction).
     * This will decrement usage counter for the corresponding version.
     * If counter reaches zero, that version (and all unused after it)
     * can be dropped immediately.
     *
     * @param txCounter to be decremented, obtained from registerVersionUsage()
     */
    public void deregisterVersionUsage(TxCounter txCounter) {
        if(txCounter != null) {
            if(txCounter.decrementAndGet() <= 0) {
                if (storeLock.isHeldByCurrentThread()) {
                    dropUnusedVersions();
                } else if (storeLock.tryLock()) {
                    try {
                        dropUnusedVersions();
                    } finally {
                        storeLock.unlock();
                    }
                }
            }
        }
    }

    private void onVersionChange(long version) {
        TxCounter txCounter = currentTxCounter;
        assert txCounter.get() >= 0;
        versions.add(txCounter);
        currentTxCounter = new TxCounter(version);
        txCounter.decrementAndGet();
        dropUnusedVersions();
    }

    private void dropUnusedVersions() {
        TxCounter txCounter;
        while ((txCounter = versions.peek()) != null
                && txCounter.get() < 0) {
            versions.poll();
        }
        setOldestVersionToKeep((txCounter != null ? txCounter : currentTxCounter).version);
    }

    private int dropUnusedChunks() {
        assert storeLock.isHeldByCurrentThread();
        int count = 0;
        if (deadChunks.isEmpty()) {
            return count;
        }

        long oldestVersionToKeep = getOldestVersionToKeep();
        long time = getTimeSinceCreation();
        saveChunkLock.lock();
        try {
            Chunk chunk;
            while ((chunk = deadChunks.poll()) != null &&
                    (isSeasonedChunk(chunk, time) && canOverwriteChunk(chunk, oldestVersionToKeep) ||
                            // if chunk is not ready yet, put it back and exit
                            // since this deque is unbounded, offerFirst() always return true
                            !deadChunks.offerFirst(chunk))) {
                if (chunks.remove(chunk.id) == null) {
                    continue;
                }
                // purge dead pages from cache
                LongArrayList toc = chunkIdToToC == null ? null : chunkIdToToC.getIfPresent(chunk.id);
                if (toc != null) {
                    chunkIdToToC.invalidate(chunk.id);
                    for (LongListIterator iterator = toc.iterator(); iterator.hasNext(); ) {
                        long tocElement = iterator.nextLong();
                        long pagePos = DataUtil.getPageInfo(chunk.id, tocElement);
                        getPageCache(DataUtil.isLeafPage(pagePos)).invalidate(pagePos);
                    }
                }

                chunkIdToChunkMetadata.remove(chunk.id);
                if (chunk.isSaved()) {
                    freeChunkSpace(chunk);
                }
                ++count;
            }
        } finally {
            saveChunkLock.unlock();
        }
        return count;
    }

    private Cache<Long, Page<?, ?>> getPageCache(boolean isLeaf) {
        return isLeaf ? leafPageCache : nonLeafPageCache;
    }

    private void freeChunkSpace(Chunk chunk) {
        long start = chunk.block * BLOCK_SIZE;
        int length = chunk.blockCount * BLOCK_SIZE;
        freeFileSpace(start, length);
    }

    private void freeFileSpace(long start, int length) {
        fileStore.free(start, length);
        if (ASSERT_MODE) {
            assert validateFileLength(start + ":" + length);
        }
    }

    private boolean validateFileLength(Object msg) {
        assert saveChunkLock.isHeldByCurrentThread();
        assert fileStore.getFileLengthInUse() == measureFileLengthInUse() :
                fileStore.getFileLengthInUse() + " != " + measureFileLengthInUse() + " " + msg.toString();
        return true;
    }

    /**
     * Class TxCounter is a simple data structure to hold version of the store
     * along with the counter of open transactions,
     * which are still operating on this version.
     */
    static final class TxCounter {

        /**
         * Version of a store, this TxCounter is related to
         */
        public final long version;

        /**
         * Counter of outstanding operation on this version of a store
         */
        private volatile int counter;

        private static final AtomicIntegerFieldUpdater<TxCounter> counterUpdater =
                                        AtomicIntegerFieldUpdater.newUpdater(TxCounter.class, "counter");


        TxCounter(long version) {
            this.version = version;
        }

        int get() {
            return counter;
        }

        /**
         * Increment and get the counter value.
         *
         * @return the new value
         */
        int incrementAndGet() {
            return counterUpdater.incrementAndGet(this);
        }

        /**
         * Decrement and get the counter values.
         *
         * @return the new value
         */
        int decrementAndGet() {
            return counterUpdater.decrementAndGet(this);
        }

        @Override
        public String toString() {
            return "v=" + version + " / cnt=" + counter;
        }
    }

    private static final class RemovedPageInfo implements Comparable<RemovedPageInfo> {
        final long version;
        final long removedPageInfo;

        RemovedPageInfo(long pagePos, boolean pinned, long version, int pageNo) {
            this.removedPageInfo = createRemovedPageInfo(pagePos, pinned, pageNo);
            this.version = version;
        }

        @Override
        public int compareTo(RemovedPageInfo other) {
            return Long.compare(version, other.version);
        }

        int getPageChunkId() {
            return DataUtil.getPageChunkId(removedPageInfo);
        }

        int getPageNo() {
            return DataUtil.getPageOffset(removedPageInfo);
        }

        int getPageLength() {
            return DataUtil.getPageMaxLength(removedPageInfo);
        }

        /**
         * Find out if removed page was pinned (can not be evacuated to a new chunk).
         * @return true if page has been pinned
         */
        boolean isPinned() {
            return (removedPageInfo & 1) == 1;
        }

        /**
         * Transforms saved page position into removed page info by
         * replacing "page offset" with "page sequential number" and
         * "page type" bit with "pinned page" flag.
         * @param pagePos of the saved page
         * @param isPinned whether page belong to a "single writer" map
         * @param pageNo 0-based sequential page number within containing chunk
         * @return removed page info that contains chunk id, page number, page length and pinned flag
         */
        private static long createRemovedPageInfo(long pagePos, boolean isPinned, int pageNo) {
            long result = (pagePos & ~((0xFFFFFFFFL << 6) | 1)) | (((long)pageNo << 6) & 0xFFFFFFFFL);
            if (isPinned) {
                result |= 1;
            }
            return result;
        }

        @Override
        public String toString() {
            return "RemovedPageInfo{" +
                    "version=" + version +
                    ", chunk=" + getPageChunkId() +
                    ", pageNo=" + getPageNo() +
                    ", len=" + getPageLength() +
                    (isPinned() ? ", pinned" : "") +
                    '}';
        }
    }

    /**
     * A builder for an MVStore.
     */
    public static final class Builder {
        private int nonLeafPageCacheSize = 16;
        private int leafPageCacheSize = 8;

        private boolean recordCacheStats;

        private boolean recoveryMode;
        private int compress = 1;

        private int pageSplitSize = Integer.MAX_VALUE;
        // IntBitPacker packs in blocks of 32 integers, LongBitPacker in 64, so, should be multiple of 32 or 64.
        // And plus 1 to encode first value, as without initial value first block will use worst bit width (as first value will be large).
        // And multiply by 2, to ensure that in case of page split still key array encoded efficiently.
        private int keysPerPage = 129;

        private int autoCommitBufferSize = 2 * 1024 * 1024;
        private int autoCommitDelay = 1_000;
        private int autoCompactFillRate = 90;
        private int versionsToKeep = 0;

        private BiConsumer<? super Throwable, ? super MVStore> backgroundExceptionHandler;

        private boolean readOnly;

        /**
         * How many versions to retain for in-memory stores. If not set, 5 old
         * versions are retained.
         *
         * @param count the number of versions to keep
         */
        public Builder versionsToKeep(int count) {
            versionsToKeep = count;
            return this;
        }

        public Builder autoCommitDelay(int ms) {
            autoCommitDelay = ms;
            return this;
        }

        public Builder autoCommitDisabled() {
            autoCommitDelay = 0;
            return this;
        }

        /**
         * Set the size of the write buffer, in KB disk space (for file-based
         * stores). Unless auto-commit is disabled, changes are automatically
         * saved if there are more than this amount of changes.
         * <p>
         * The default is 2 MB.
         * <p>
         * When the value is set to 0 or lower, data is not automatically
         * stored.
         */
        public Builder autoCommitBufferSize(int kb) {
            autoCommitBufferSize = kb * 1024;
            return this;
        }

        /**
         * Set the auto-compact target fill rate. If the average fill rate (the
         * percentage of the storage space that contains active data) of the
         * chunks is lower, then the chunks with a low fill rate are re-written.
         * Also, if the percentage of empty space between chunks is higher than
         * this value, then chunks at the end of the file are moved. Compaction
         * stops if the target fill rate is reached.
         * <p>
         * The default value is 90 (90%). The value 0 disables auto-compacting.
         * <p>
         */
        public Builder autoCompactFillRate(int percent) {
            autoCompactFillRate = percent;
            return this;
        }

        /**
         * Open the file in read-only mode. In this case, a shared lock will be
         * acquired to ensure the file is not concurrently opened in write mode.
         * <p>
         * If this option is not used, the file is locked exclusively.
         * <p>
         * Please note a store may only be opened once in every JVM (no matter
         * whether it is opened in read-only or read-write mode), because each
         * file may be locked only once in a process.
         *
         * @return this
         */
        public Builder readOnly() {
            readOnly = true;
            return this;
        }

        /**
         * Set the number of keys per page.
         */
        public Builder keysPerPage(int keysPerPage) {
            this.keysPerPage = keysPerPage;
            return this;
        }

        /**
         * Open the file in recovery mode, where some errors may be ignored.
         */
        public Builder recoveryMode() {
            recoveryMode = true;
            return this;
        }

        /**
         * Set the read non-leaf page cache size in MB. The default is 16 MB.
         */
        public Builder nonLeafPageCacheSize(int sizeInMb) {
            nonLeafPageCacheSize = sizeInMb;
            return this;
        }

        /**
         * Set the read leaf cache size in MB. The default is 8 MB.
         */
        public Builder cacheSize(int sizeInMb) {
            leafPageCacheSize = sizeInMb;
            return this;
        }

        public Builder recordCacheStats(boolean recordCacheStats) {
            this.recordCacheStats = recordCacheStats;
            return this;
        }

        /**
         * Compress data before writing using the LZ4 algorithm. This will
         * save more disk space, but will slow down read and write operations
         * quite a bit.
         * <p>
         * This setting only affects writes; it is not necessary to enable
         * compression when reading, even if compression was enabled when
         * writing.
         *
         * @return this
         */
        public Builder compress() {
            compress = 1;
            return this;
        }

        /**
         * Compress data before writing using the LZ4 algorithm (level 1) or LZ4HC (level 2).
         * Use level 0 to disable compression. By default level 1 (LZ4).
         *
         * @return this
         */
        public Builder compressionLevel(int level) {
            compress = level;
            return this;
        }

        /**
         * Set the amount of memory a page should contain at most, in bytes,
         * before it is split. The default is 16 KB for persistent stores and 4
         * KB for in-memory stores. This is not a limit in the page size, as
         * pages with one entry can get larger. It is just the point where pages
         * that contain more than one entry are split.
         */
        public Builder pageSplitSize(int pageSplitSize) {
            this.pageSplitSize = pageSplitSize;
            return this;
        }

        /**
         * Set the listener to be used for exceptions that occur when writing in
         * the background thread.
         */
        public Builder backgroundExceptionHandler(BiConsumer<? super Throwable, ? super MVStore> exceptionHandler) {
            backgroundExceptionHandler = exceptionHandler;
            return this;
        }

        public MVStore open(@NotNull Path file) throws IOException {
            return new MVStore(createFileStore(file, false, readOnly ? FileStore.R : FileStore.RW), this);
        }

        public MVStore truncateAndOpen(@NotNull Path file) throws IOException {
            assert !readOnly;
            return new MVStore(createFileStore(file, false, FileStore.RW_TRUNCATE), this);
        }

        /**
         * Open database file or a create new one if IO exception occurred.
         */
        public MVStore openOrNewOnIoError(@NotNull Path file, boolean useFileCache, @NotNull Consumer<? super Exception> errorConsumer) {
            assert !readOnly;
            FileStore fileStore = null;
            try {
                fileStore = createFileStore(file, useFileCache, FileStore.RW);
                return new MVStore(fileStore, this);
            }
            catch (IOException e) {
                errorConsumer.accept(e);
                // file is not deleted - flag TRUNCATE_EXISTING is used instead
            }
            catch (MVStoreException e) {
                if (e.getErrorCode() == MVStoreException.ERROR_FILE_LOCKED) {
                    throw e;
                }
                if (fileStore != null) {
                    try {
                        fileStore.close();
                    }
                    catch (Exception ignore) {
                    }
                }
                errorConsumer.accept(e);
            }
            return new MVStore(truncateAndOpen(file, useFileCache), this);
        }

        private static @NotNull FileStore truncateAndOpen(@NotNull Path file, boolean useFileCache) {
            FileStore fileStore;
            try {
                fileStore = createFileStore(file, useFileCache, FileStore.RW_TRUNCATE);
            }
            catch (IOException e) {
                // fatal error
                throw new UncheckedIOException(e);
            }
            return fileStore;
        }

        private @NotNull static FileStore createFileStore(@NotNull Path file, boolean useFileCache, Set<? extends OpenOption> options) throws IOException {
            return new FileStore(file, options, useFileCache);
        }

        @Override
        public String toString() {
            return "Builder(" +
                   "nonLeafPageCacheSize=" + nonLeafPageCacheSize +
                   ",leafPageCacheSize=" + leafPageCacheSize +
                   ", recoveryMode=" + recoveryMode +
                   ", compress=" + compress +
                   ", pageSplitSize=" + pageSplitSize +
                   ", keysPerPage=" + keysPerPage +
                   ", autoCommitBufferSize=" + autoCommitBufferSize +
                   ", autoCommitDelay=" + autoCommitDelay +
                   ", autoCompactFillRate=" + autoCompactFillRate +
                   ", backgroundExceptionHandler=" + backgroundExceptionHandler +
                   ", readOnly=" + readOnly +
                   ')';
        }
    }
}
