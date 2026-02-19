// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.openapi.util.Pair;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Implementation of <a href="https://en.wikipedia.org/wiki/Extendible_hashing">Extendible hash map</a>
 * Basically, it is a durable map that unions N=2^k fixed-size open-addressing hash maps (segments) and
 * distribute keys to one of those maps based on hash suffix (k-bits).
 * Being overly filled, a segment splits into 2 new segments (and hash suffix depth is incremented).
 * Split complexity is ~segment_size, and since segment size is fixed, this makes split cost also
 * fixed -- i.e., independent of table size. Segment-local split is also convenient for implementation
 * on top of durable storage, since each split 'touches' only a limited (and known beforehand) range of
 * durable storage data.
 * For more details, refer to the wiki article above.
 * <p>
 * Threading: so far implementation is guarded by a single lock, so it is thread-safe but
 * not concurrent. It is possible to replace that with some kind of 'segmented RW lock'.
 */
@ApiStatus.Internal
public class ExtendibleHashMap implements DurableIntToMultiIntMap, Unmappable {
  /** Version of binary format used by this class */
  public static final int IMPLEMENTATION_VERSION = 1;

  /** First header int32 to recognize this file type */
  public static final int MAGIC_WORD = IOUtil.asciiToMagicWord("EHMM");

  //Binary layout: (header segment) (data segment)+
  //  Header segment: (fixed header fields) (segments table)
  //                  fixed header fields: 80 bytes
  //                                       magicWord, versions, etc...
  //                                       segmentsTableSize: int32
  //                                       segmentsCount:     int32
  //                                       globalDepth:       int8
  //                                       ...
  //                  segments table: int16[N], contains data segment directory
  //  Data segment:  (fixed segment header) (hashtable data)
  //                 fixed segment header: 16 bytes
  //                                       segment live entries count: int32
  //                                       segment hash suffix:        int32
  //                                       segment hash suffix depth:  int8
  //                                       ...
  //                 hashtable slots: int32[N]
  //
  //Header and data segments are of same size -- this simplifies overall layout, since we need to align everything
  // to a page size, and it is much easier to align fixed size segments -- just (pageSize % segmentSize = 0).


  //Segment size: 32K seems like a good segment size: header segment could address (32k-80)/2 ~= 16k segments,
  // each segment contains (32k-16)/4/2 ~= 4k of (key:int32,value:int32) entries.
  // Open addressing hashmaps usually sized with loadFactor ~= 0.5, so 4k entries mean ~2k useful payload.
  // In total, ~16k segments of ~2k key-value pairs each gives us 32M entries max -- suitable for most purposes
  // (BEWARE: currently only 16M entries could be used, see comment in HeaderLayout)
  // 64k segments make it 4x, which should be enough for everyone.

  public static final int DEFAULT_SEGMENT_SIZE = 1 << 15;   // =32k
  public static final int DEFAULT_SEGMENTS_PER_PAGE = 32;
  /** 1M page ~= 64K (key,value) pairs per page */
  public static final int DEFAULT_STORAGE_PAGE_SIZE = DEFAULT_SEGMENT_SIZE * DEFAULT_SEGMENTS_PER_PAGE;

  private static final boolean MARK_SAFELY_CLOSED_ON_FLUSH = getBooleanProperty("ExtendibleHashMap.MARK_SAFELY_CLOSED_ON_FLUSH", true);

  //TODO RC: unfinished work
  //        0) remove 'synchronized' and put synchronization on clients to decide?
  //        1) prune tombstones (see comment in a .split() method for details)
  //        2) .size() is now O(N), make it O(1)
  //        3) Under-utilized (< 50%-utilized) segmentTable room (see HeaderLayout comments)

  private final MMappedFileStorage storage;
  private transient BufferSource bufferSource;

  /** Used to avoid updating header.fileState on _each_ modification */
  private boolean dirty = false;

  private final boolean wasProperlyClosed;

  private transient HeaderLayout header;

  /**
   * Map[segmentIndex -> HashMapSegmentLayout]
   * Segments are quite light, but there are queried very frequently -- and there are not too many of them.
   * So cache them instead of allocating a new instance each time.
   */
  private final transient Int2ObjectMap<HashMapSegmentLayout> segmentsCache = new Int2ObjectOpenHashMap<>();

  private final transient HashMapAlgo hashMapAlgo = new HashMapAlgo(0.5f);

  public ExtendibleHashMap(@NotNull MMappedFileStorage storage,
                           int segmentSize) throws IOException {
    if (Integer.bitCount(segmentSize) != 1) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be power of 2");
    }
    int pageSize = storage.pageSize();
    if (segmentSize > pageSize) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be <= pageSize(=" + pageSize + ")");
    }
    if ((pageSize % segmentSize) != 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must align with pageSize(=" + pageSize + ")");
    }

    synchronized (this) {
      this.storage = storage;
      boolean fileIsEmpty = (storage.actualFileSize() == 0);

      bufferSource = new BufferSourceOverMMappedFileStorage(storage);

      header = new HeaderLayout(bufferSource, segmentSize);

      if (fileIsEmpty) {
        initEmptyMap(segmentSize);
        wasProperlyClosed = true; //new empty storage is by definition 'correct'
      }
      else {
        int magicWord = header.magicWord();
        if (magicWord != MAGIC_WORD) {
          throw new IOException(
            "[" + storage.storagePath() + "] is of incorrect type: " +
            ".magicWord(=" + magicWord + ", '" + IOUtil.magicWordToASCII(magicWord) + "') != " + MAGIC_WORD + " expected");
        }

        if (header.version() != IMPLEMENTATION_VERSION) {
          throw new IOException(
            "[" + storage.storagePath() + "]: version(=" + header.version() + ") != current impl version(=" + IMPLEMENTATION_VERSION + ")");
        }

        if (header.segmentSize() != segmentSize) {
          throw new IOException(
            "[" + storage.storagePath() + "]: segmentSize(=" + segmentSize + ") != segmentSize(=" + header.segmentSize() + ")" +
            " storage was initialized with");
        }

        wasProperlyClosed = (header.fileStatus() == HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);
        //and reset the file status to default 'properly closed'
        header.fileStatus(HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);
      }
    }
  }

  private void initEmptyMap(int segmentSize) throws IOException {
    header.magicWord(MAGIC_WORD);
    header.version(IMPLEMENTATION_VERSION);
    header.segmentSize(segmentSize);
    header.fileStatus(HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);


    header.globalHashSuffixDepth(0);
    header.actualSegmentsCount(0);

    HashMapSegmentLayout segment = allocateSegment(0, header.globalHashSuffixDepth());
    header.updateSegmentIndex(0, segment.segmentIndex());
  }

  /**
   * Was storage properly closed in a previous session?
   * <p/>
   * Just created storage is defined as 'properly closed', existing storage is 'properly closed'  if {@link #close()}
   * method was called in a previous session. This flag could be used to detect potential data corruptions -- so
   * storage must be thoroughly checked -- or re-created.
   * <p/>
   * 'Properly closed' status is only about previous session. I.e., consider a scenario:
   * <pre>
   * storage opened
   * -> not closed properly
   * -> re-opened: wasProperlyClose=false
   * -> closed properly
   * -> re-opened: wasProperlyClosed=true
   * </pre>
   * So on 2nd re-open storage is 'properly closed', even though it could be still corrupted because of the absence
   * of proper close after the first session.
   */
  public synchronized boolean wasProperlyClosed() {
    return wasProperlyClosed;
  }

  @Override
  public synchronized boolean put(int key,
                                  int value) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    return putAndSplitSegmentIfNeeded(segment, key, value);
  }

  @Override
  public synchronized boolean has(int key,
                                  int value) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    return hashMapAlgo.has(segment, key, value);
  }

  @Override
  public synchronized int lookup(int key,
                                 @NotNull ValueAcceptor valuesAcceptor) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    return hashMapAlgo.lookup(segment, key, valuesAcceptor);
  }

  @Override
  public synchronized int lookupOrInsert(int key,
                                         @NotNull ValueAcceptor valuesAcceptor,
                                         @NotNull ValueCreator valueCreator) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    int valueFound = hashMapAlgo.lookup(segment, key, valuesAcceptor);
    if (valueFound != NO_VALUE) {
      return valueFound;
    }
    int newValue = valueCreator.newValueForKey(key);
    boolean reallyPut = putAndSplitSegmentIfNeeded(segment, key, newValue);
    assert reallyPut : key + " must be really put since we've checked it wasn't there";
    return newValue;
  }

  @Override
  public synchronized boolean remove(int key,
                                     int value) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    return hashMapAlgo.remove(segment, key, value);
  }

  @Override
  public synchronized boolean replace(int key,
                                      int oldValue,
                                      int newValue) throws IOException {
    HashMapSegmentLayout segment = segmentForKey(key);

    return hashMapAlgo.replace(segment, key, oldValue, newValue);
  }

  @Override
  public synchronized int size() throws IOException {
    checkNotClosed();
    //FIXME RC: it is O(#segments) now, but O(1) would be better -> just keep recordsCount in a header
    int segmentSize = header.segmentSize();
    int segmentsCount = header.actualSegmentsCount();
    int totalEntries = 0;
    for (int segmentIndex = 1; segmentIndex <= segmentsCount; segmentIndex++) {
      totalEntries += HashMapSegmentLayout.aliveEntriesCount(bufferSource, segmentIndex, segmentSize);
    }
    return totalEntries;
  }

  @Override
  public synchronized boolean isEmpty() throws IOException {
    checkNotClosed();
    int segmentSize = header.segmentSize();
    int segmentsCount = header.actualSegmentsCount();
    for (int segmentIndex = 1; segmentIndex <= segmentsCount; segmentIndex++) {
      int aliveEntriesCount = HashMapSegmentLayout.aliveEntriesCount(bufferSource, segmentIndex, segmentSize);
      if (aliveEntriesCount > 0) {
        return false;
      }
    }
    return true;
  }

  /** @return false if iteration was cancelled early, by processor returning false, true if all items were processed */
  @Override
  public synchronized boolean forEach(@NotNull KeyValueProcessor processor) throws IOException {
    checkNotClosed();
    int segmentSize = header.segmentSize();
    int segmentsCount = header.actualSegmentsCount();
    for (int segmentIndex = 1; segmentIndex <= segmentsCount; segmentIndex++) {
      HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, segmentSize);
      if (segment.aliveEntriesCount() > 0) {
        if (!hashMapAlgo.forEach(segment, processor)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public synchronized void clear() throws IOException {
    checkNotClosed();
    if (header.actualSegmentsCount() == 1) {
      //isEmpty() could be O(#segments), hence first check the actualSegmentsCount: if there is only a single segment,
      // it _could_ be the map was just created => empty:
      if (isEmpty()) {
        return;
      }
      //...otherwise the map could be _logically_ empty, but physically still have many segments filled with tombstones -- in
      // which case we still want to clear them all, even though logically we could just keep the map as-is.
      //MAYBE RC: really, even if there is a single segment -- it could be filled with tombstones, so may ask for cleaning
    }
    int segmentSize = header.segmentSize();

    segmentsCache.clear();

    storage.zeroizeTillEOF(0);
    //MAYBE RC: zeroize() is not very fast, and also mmapped file size remains the same, hence it still occupies a significant space
    //          on disk. Better having something like storage.truncate() -- but it is hard to implement cross-platform for memory-mapped
    //          files
    initEmptyMap(segmentSize);
    dirty = true;
  }


  @Override
  public synchronized void flush() throws IOException {
    if (MARK_SAFELY_CLOSED_ON_FLUSH) {
      //RC: Since EHMap is non-concurrent (sync-ed), it seems safe to set .fileStatus(PROPERLY_CLOSED) in
      //    .flush(): nobody could modify EHMap content until .flush() finishes, which creates kind of
      //    'safepoint'.
      //    (On the contrary: data structures with concurrent updates don't have this property: their
      //    content could be modified in between .flush() sets .dirty=false and .fileStatus=PROPERLY_CLOSED)

      if (dirty) {
        dirty = false;
        header.fileStatus(HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);
      }
    }
  }

  public synchronized boolean isDirty() {
    return dirty;
  }

  @Override
  public synchronized void close() throws IOException {
    if (storage.isOpen()) {
      if (dirty) {
        header.fileStatus(HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);
      }
      storage.close();

      //clean all references to mapped ByteBuffers, so it's easier for GC to unmap them:
      segmentsCache.clear();
      header = null;
      bufferSource = null;
    }
  }

  @Override
  public synchronized boolean isClosed() {
    return !storage.isOpen();
  }

  @Override
  public synchronized void closeAndUnsafelyUnmap() throws IOException {
    close();
    storage.closeAndUnsafelyUnmap();
  }

  @Override
  public synchronized void closeAndClean() throws IOException {
    close();
    storage.closeAndClean();
  }

  @Override
  public String toString() {
    return "ExtendibleHashMap" +
           "[" + storage.storagePath() + "]" +
           "[opened: " + storage.isOpen() + "]" +
           "[wasProperlyClosed: " + wasProperlyClosed + "]";
  }

  //=============== implementation ============================================================

  //@GuardedBy(this)
  private void checkNotClosed() throws IOException {
    if (!storage.isOpen()) {
      throw new ClosedStorageException("Storage [" + storage + "] is closed");
    }
  }

  //@GuardedBy(this)
  private void markModified() {
    if (!dirty) {
      dirty = true;
      header.fileStatus(HeaderLayout.FILE_STATUS_OPENED);
    }
  }

  //@GuardedBy(this)
  private HashMapSegmentLayout segmentForKey(int key) throws IOException {
    checkNotClosed();

    int hash = hash(key);
    int segmentIndex = header.segmentIndexByHash(hash);

    HashMapSegmentLayout layout = segmentsCache.get(segmentIndex);
    if (layout == null) {
      layout = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());
      segmentsCache.put(segmentIndex, layout);
    }
    return layout;
  }

  //@GuardedBy(this)
  private void splitAndRearrangeEntries(HashMapSegmentLayout segment) throws IOException {
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int segmentHashDepth = segment.hashSuffixDepth();
    if (hashSuffixDepth == segmentHashDepth) {
      doubleSegmentsTable();
    }
    assert header.globalHashSuffixDepth() > segment.hashSuffixDepth()
      : "globalHashSuffixDepth(=" + header.globalHashSuffixDepth() + ") " +
        "must be > segment.hashSuffixDepth(=" + segment.hashSuffixDepth() + ")";

    Pair<HashMapSegmentLayout, HashMapSegmentLayout> splitSegments = split(segment);
    HashMapSegmentLayout oldSegment = splitSegments.first;
    HashMapSegmentLayout newSegment = splitSegments.second;

    //redirect each 2nd oldSegment reference to the newSegment:
    int[] newSegmentSlotIndexes = slotIndexesForSegment(
      newSegment.hashSuffix(),
      newSegment.hashSuffixDepth(),
      header.globalHashSuffixDepth()
    );
    for (int segmentSlotIndex : newSegmentSlotIndexes) {
      int segmentIndex = header.segmentIndex(segmentSlotIndex);
      assert segmentIndex == oldSegment.segmentIndex()
        : "segment[" + segmentSlotIndex + "].segmentIndex(=" + segmentIndex + ") but it must be " + oldSegment.segmentIndex();

      header.updateSegmentIndex(segmentSlotIndex, newSegment.segmentIndex());
    }
  }

  /**
   * @return indexes under which segment with segmentHashSuffix must be referenced in a segments directory
   * in a header
   */
  @VisibleForTesting
  public static int[] slotIndexesForSegment(int segmentHashSuffix,
                                            byte segmentHashSuffixDepth,
                                            byte globalHashSuffixDepth) {
    assert (segmentHashSuffix & ~suffixMask(segmentHashSuffixDepth)) == 0;
    assert globalHashSuffixDepth >= segmentHashSuffixDepth
      : "globalDepth(=" + globalHashSuffixDepth + ") must be >= segmentDepth(=" + segmentHashSuffixDepth + ")";

    //If a segment has segmentHashSuffix, when all segmentSlotIndexes with such bit-suffix should point to
    // it (btw, this is how keys with segment.hashSuffix end up in that segment).
    // So we need to generate all indexes in [0..2^globalHashSuffixDepth) with [segment.hashSuffix]: we iterate
    // through all possible (globalDepth-segmentDepth) 'free' prefix bits, while keeping [segmentHashSuffixDepth]
    // tail bits =[segmentHashSuffix]
    int indexesCount = 1 << (globalHashSuffixDepth - segmentHashSuffixDepth);
    int[] slotIndexes = new int[indexesCount];
    for (int i = 0; i < indexesCount; i++) {
      int prefix = i << segmentHashSuffixDepth;
      slotIndexes[i] = prefix | segmentHashSuffix;
    }
    return slotIndexes;
  }

  //@GuardedBy(this)
  private boolean putAndSplitSegmentIfNeeded(HashMapSegmentLayout segment,
                                             int key,
                                             int value) throws IOException {
    boolean wasReallyPut = hashMapAlgo.put(segment, key, value);

    if (wasReallyPut) {
      markModified();
    }

    if (hashMapAlgo.needsSplit(segment)) {
      splitAndRearrangeEntries(segment);
    }
    return wasReallyPut;
  }

  /**
   * Creates new segment with (hashSuffix, hashSuffixDepth), increase header.actualSegmentCount, but DO NOT ADD
   * new segment into a header directory (segmentsTable) -- this is left up to calling code.
   * MAYBE: probably we could also encapsulate that last bit inside the method: knowing (hashSuffix, hashSuffixDepth)
   * and header.globalHashSuffixDepth is enough to know which slots in a segments directory should point towards
   * new segment. Such semantics for allocateSegment() is quite convenient: new segment is always properly 'attached'
   * into a overall structure, there is no way calling code could attach it incorrectly.
   */
  //@GuardedBy(this)
  private HashMapSegmentLayout allocateSegment(int hashSuffix,
                                               byte hashSuffixDepth) throws IOException {
    int segmentsCount = header.actualSegmentsCount();
    int segmentIndex = segmentsCount + 1;// segmentIndex starts with 1 (segmentIndex=0 is the header)

    HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());
    segment.updateHashSuffix(hashSuffix, hashSuffixDepth);

    header.actualSegmentsCount(segmentsCount + 1);

    return segment;
  }

  /**
   * Splits given segment: increments hashSuffixDepth, allocates new segment, move entries with newly added bit=1
   * to new segment, removing them from segmentToSplit.
   *
   * @return pair [segmentToSplit, newSegment], there segmentToSplit contains entries with hash=...0..., while
   * newSegment contains entries with hash=...1...
   */
  //@GuardedBy(this)
  private Pair<HashMapSegmentLayout, HashMapSegmentLayout> split(@NotNull HashMapSegmentLayout segmentToSplit) throws IOException {
    int oldHashSuffix = segmentToSplit.hashSuffix();
    byte oldHashSuffixDepth = segmentToSplit.hashSuffixDepth();

    byte newHashSuffixDepth = (byte)(oldHashSuffixDepth + 1);
    int highestSuffixBit = 1 << (newHashSuffixDepth - 1);
    int hashSuffix0 = oldHashSuffix;                     // ...000[oldHashSuffix]
    int hashSuffix1 = oldHashSuffix | highestSuffixBit;  // ...001[oldHashSuffix]
    assert hashSuffix0 != hashSuffix1
      : "hashSuffixes must be different for splitting segments, but " + hashSuffix0 + " == " + hashSuffix1;

    HashMapSegmentLayout newSegment = allocateSegment(hashSuffix1, newHashSuffixDepth);
    segmentToSplit.updateHashSuffix(hashSuffix0, newHashSuffixDepth);

    //Transfer entries that are not belonged to segmentToSplit with new hashSuffix0/newHashSuffixDepth
    // delete those entries from segmentToSplit and move them to newSegment:
    int hashSuffixMask = segmentToSplit.hashSuffixMask(); // ...001_00... -> ...000_11...
    int entriesCount = segmentToSplit.entriesCount();
    for (int i = 0; i < entriesCount; i++) {
      int key = segmentToSplit.entryKey(i);
      if (hashMapAlgo.isSlotOccupied(key)) {
        int hash = hash(key);
        if ((hash & hashSuffixMask) != hashSuffix0) {
          int value = segmentToSplit.entryValue(i);

          hashMapAlgo.markEntryAsDeleted(segmentToSplit, i);

          hashMapAlgo.put(newSegment, key, value);
        }
      }
      //MAYBE RC: possible performance issue here: each time we split segments, a lot of tombstones are
      // accumulated in the old segment -- for every entry moved to a new segment we leave tombstone behind.
      // So after the split old segment contain ~25% of total entries as tombstones (50% fill is a split
      // trigger, 1/2 entries moved to new segment on split). Next split adds another 25% tombstones, and
      // so on. This is not strictly additive process -- it is not that 100% entries will be tombstones
      // after 4 splits -- tombstones get reused, but still accumulation is possible, and abundance of
      // tombstones could significantly increase probing length even for low-load-factor tables.
      // In a regular (in-memory) hashtables this is not an issue, since we get rid of all tombstones on
      // each resize, while copying only alive values into a new table. Here this mechanism is not exists,
      // because instead of _recreate_ the table on each resize we just add new segment, leaving old segment
      // as-is.
      // Solution is a regular pruning: keep track of segment tombstone count, and re-hash segment if there
      // are too many tombstones. Since segment size is fixed and not too big, we could copy alive entries
      // in memory buffer, clean the segment entries table, and insert values back into it -- simple and fast.
    }

    return Pair.pair(segmentToSplit, newSegment);
  }

  /**
   * Doubles segments table size, and copies entries from the first half into a second (new) half.
   * E.g. table[1,2,3] after doubling become [1,2,3, 1,2,3]
   */
  //@GuardedBy(this)
  private void doubleSegmentsTable() throws IOException {
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int oldTableSize = header.segmentTableSize();
    if (oldTableSize * 2 > header.maxSegmentTableSize()) {
      throw new IllegalStateException(
        "Can't expand table: currentSize=" + header.segmentTableSize() +
        " x2 > maxSize=" + header.maxSegmentTableSize() +
        " -> try increase segmentSize(=" + header.segmentSize() + ") if you need to store more keys"
      );
    }
    header.globalHashSuffixDepth(hashSuffixDepth + 1);
    for (int i = 0; i < oldTableSize; i++) {
      int segmentIndex = header.segmentIndex(i);
      header.updateSegmentIndex(oldTableSize + i, segmentIndex);
    }
  }

  private static int segmentSlotIndex(int hash,
                                      int hashSuffixDepth) {
    int segmentMask = suffixMask(hashSuffixDepth);
    return hash & segmentMask;
  }

  /**
   * @return bitmask for last (less-significant) suffixSize bits
   * E.g. suffixMask(3) returns ...00111
   */
  private static int suffixMask(int suffixSize) {
    if (suffixSize == Integer.SIZE) {
      return -1;
    }
    return (1 << suffixSize) - 1;
  }

  private static final int INT_GOLDEN_RATIO = 0x9E3779B9;

  /** aka 'fibonacci hashing' */
  private static int hash(int key) {
    final int h = key * INT_GOLDEN_RATIO;
    return h ^ (h >>> 16);
  }


  static final class HeaderLayout {
    //@formatter:off
    public static final int MAGIC_WORD_OFFSET               =  0;  //int32
    public static final int VERSION_OFFSET                  =  4;  //int32
    public static final int SEGMENT_SIZE_OFFSET             =  8;  //int32
    public static final int ACTUAL_SEGMENTS_COUNT_OFFSET    = 12;  //int32
    public static final int GLOBAL_HASH_SUFFIX_DEPTH_OFFSET = 16;  //int8

    public static final int FILE_STATUS_OFFSET              = 17;  //int8

    public static final int FIRST_FREE_OFFSET               = 18;
    // region [18..79] is reserved for the generations to come
    public static final int STATIC_HEADER_SIZE              = 80;

    private static final int SEGMENTS_TABLE_OFFSET = STATIC_HEADER_SIZE; //int16[N]
    //@formatter:on

    public static final byte FILE_STATUS_PROPERLY_CLOSED = 1;
    public static final byte FILE_STATUS_OPENED = 0;

    //TODO RC: segmentSize is 2^N, and segmentsTable also must have 2^M entries -- but since we use few bytes
    // for static header, this means M <= N-1 -- i.e. we waste _almost half_ of header segment space because
    // segmentsTable must have power-of-2 size.
    // We could solve that by using some compression -- we need to win just 80 bytes out of 32-64k, it seems
    // quite doable, and we could cache uncompressed segmentsTable in memory -- 32-64k heap usage is OK given
    // it makes hashtable almost 2x larger for same segmentSize, and likely also speeds up segmentTable lookup
    // a bit.
    // Alternative solution would be to untie (headerSegment.size == dataSegment.size) constraint: i.e. allow
    // header to be (80 + dataSegment.size) bytes. Such a change of layout means we can't align segments to the
    // first page anymore -- so there will be (dataSegment.size-80) wasted bytes at the end of the first page.
    // But this still seems a better tradeoff than the first option. And even better: since OS virtual memory page
    // size (4k-16k) usually smaller than segmentSize (32k-64k) - only a part (< 50%) of those 32k-64k of wasted
    // space would really waste _RAM_.

    private final ByteBuffer headerBuffer;

    /** headerSegmentSize == {@link #segmentSize()} (we check that in ctor), but we cache it in field since it is frequently used */
    private final transient int headerSegmentSize;

    HeaderLayout(@NotNull BufferSource bufferSource,
                 int headerSegmentSize) throws IOException {
      if (headerSegmentSize <= STATIC_HEADER_SIZE) {
        throw new IllegalArgumentException("headerSize(=" +
                                           headerSegmentSize + ") must be > STATIC_HEADER_SIZE(=" + STATIC_HEADER_SIZE + ")");
      }

      this.headerSegmentSize = headerSegmentSize;
      headerBuffer = bufferSource.slice(0, headerSegmentSize);
    }

    public int magicWord() {
      return headerBuffer.getInt(MAGIC_WORD_OFFSET);
    }

    public void magicWord(int magicWord) {
      headerBuffer.putInt(MAGIC_WORD_OFFSET, magicWord);
    }

    public int version() {
      return headerBuffer.getInt(VERSION_OFFSET);
    }

    public void version(int version) {
      headerBuffer.putInt(VERSION_OFFSET, version);
    }

    public int segmentSize() {
      return headerBuffer.getInt(SEGMENT_SIZE_OFFSET);
    }

    public void segmentSize(int size) {
      headerBuffer.putInt(SEGMENT_SIZE_OFFSET, size);
    }

    public byte fileStatus() {
      return headerBuffer.get(FILE_STATUS_OFFSET);
    }

    public void fileStatus(@MagicConstant(intValues = {FILE_STATUS_PROPERLY_CLOSED, FILE_STATUS_OPENED})
                           byte connectionStatus) {
      headerBuffer.put(FILE_STATUS_OFFSET, connectionStatus);
    }

    public static int magicWord(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getInt(MAGIC_WORD_OFFSET);
    }

    public static int version(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getInt(VERSION_OFFSET);
    }

    public static int segmentSize(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getInt(SEGMENT_SIZE_OFFSET);
    }

    public static byte fileStatus(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.get(FILE_STATUS_OFFSET);
    }


    public int actualSegmentsCount() {
      return headerBuffer.getInt(ACTUAL_SEGMENTS_COUNT_OFFSET);
    }

    public void actualSegmentsCount(int count) {
      headerBuffer.putInt(ACTUAL_SEGMENTS_COUNT_OFFSET, count);
    }

    /** How many trailing bits of key.hash to use to determine segment to store the key */
    public byte globalHashSuffixDepth() {
      return headerBuffer.get(GLOBAL_HASH_SUFFIX_DEPTH_OFFSET);
    }

    public void globalHashSuffixDepth(int depth) {
      if (depth < 0 || depth >= Integer.SIZE) {
        throw new IllegalArgumentException("depth(=" + depth + ") must be in [0..32)");
      }
      headerBuffer.put(GLOBAL_HASH_SUFFIX_DEPTH_OFFSET, (byte)depth);
    }

    /** @return segmentIndex in [1..actualSegmentsCount], for slotIndex in [0..segmentTableSize) */
    public int segmentIndex(int slotIndex) throws IOException {
      Objects.checkIndex(slotIndex, segmentTableSize());
      return Short.toUnsignedInt(headerBuffer.getShort(SEGMENTS_TABLE_OFFSET + slotIndex * Short.BYTES));
    }

    public int segmentIndexByHash(int hash) throws IOException {
      int hashSuffixDepth = globalHashSuffixDepth();
      int segmentSlotIndex = segmentSlotIndex(hash, hashSuffixDepth);
      int segmentIndex = segmentIndex(segmentSlotIndex);
      if (segmentIndex < 1) {
        throw new CorruptedException(
          "segmentIndex[hash: " + hash + ", suffix: " + hashSuffixDepth + ", slotIndex: " + segmentSlotIndex + "](= " + segmentIndex + ")" +
          " must be >=1 => .segmentsTable is corrupted"
        );
      }
      return segmentIndex;
    }

    public void updateSegmentIndex(int slotIndex,
                                   int segmentIndex) {
      Objects.checkIndex(slotIndex, segmentTableSize());
      if (segmentIndex < 1 || segmentIndex > 0xFFFF) {
        throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be in [1..0xFFFF]");
      }
      headerBuffer.putShort(SEGMENTS_TABLE_OFFSET + slotIndex * Short.BYTES, (short)segmentIndex);
    }

    public int segmentTableSize() {
      return 1 << globalHashSuffixDepth();
    }

    public int maxSegmentTableSize() {
      return (headerSegmentSize - STATIC_HEADER_SIZE) / Short.BYTES;
    }

    @Override
    public String toString() {
      return "HeaderLayout[headerSize=" + headerSegmentSize + ']';
    }

    public String dump() throws IOException {
      StringBuilder sb =
        new StringBuilder("HeaderLayout[size: " + headerSegmentSize + "b, globalHashSuffixSize: " + globalHashSuffixDepth() + "]");
      sb.append("[tableSize: " + segmentTableSize() + ", actualSegments: " + actualSegmentsCount() + "]\n");
      for (int i = 0; i < segmentTableSize(); i++) {
        sb.append("\t[" + i + "]=" + segmentIndex(i) + "\n");
      }
      return sb.toString();
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public record HashMapSegmentLayout(int segmentIndex, int segmentSize, @NotNull ByteBuffer segmentBuffer) implements HashTableData {
    //@formatter:off
    private static final int LIVE_ENTRIES_COUNT_OFFSET  =  0; //int32
    private static final int HASH_SUFFIX_OFFSET         =  4; //int32
    private static final int HASH_SUFFIX_DEPTH_OFFSET   =  8; //int8
    // == region [9..15] is reserved for generations to come
    private static final int STATIC_HEADER_SIZE         = 16;

    private static final int HASHTABLE_SLOTS_OFFSET     = STATIC_HEADER_SIZE; //int32[N]
    //@formatter:on

    public HashMapSegmentLayout {
      if (segmentIndex < 1) {
        throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be >=1 (0-th segment is a header)");
      }
    }

    @VisibleForTesting
    public HashMapSegmentLayout(@NotNull BufferSource bufferSource,
                                int segmentIndex,
                                int segmentSize) throws IOException {
      this(segmentIndex, segmentSize,
           bufferSource.slice(segmentIndex * (long)segmentSize, segmentSize)
      );
    }

    @Override
    public int aliveEntriesCount() {
      return segmentBuffer.getInt(LIVE_ENTRIES_COUNT_OFFSET);
    }

    void clear() {
      updateAliveEntriesCount(0);
      //TODO RC: implement -- clear all slots, and update hashSuffix (how?)
      throw new UnsupportedOperationException("Method not implemented yet");
    }

    /**
     * 'Inlined' version of {@code new HashMapSegmentLayout(bufferSource, segmentIndex, segmentSize).aliveEntriesCount()}
     * with reduced allocations and slicing
     */
    public static int aliveEntriesCount(@NotNull BufferSource bufferSource,
                                        int segmentIndex,
                                        int segmentSize) throws IOException {
      if (segmentIndex < 1) {
        throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be >=1 (0-th segment is a header)");
      }
      long offsetInFile = segmentIndex * (long)segmentSize;
      return bufferSource.getInt(offsetInFile + LIVE_ENTRIES_COUNT_OFFSET);
    }

    @Override
    public void updateAliveEntriesCount(int aliveCount) {
      segmentBuffer.putInt(LIVE_ENTRIES_COUNT_OFFSET, aliveCount);
    }

    public byte hashSuffixDepth() {
      return segmentBuffer.get(HASH_SUFFIX_DEPTH_OFFSET);
    }

    public int hashSuffix() {
      return segmentBuffer.getInt(HASH_SUFFIX_OFFSET);
    }

    public int hashSuffixMask() {
      return suffixMask(hashSuffixDepth());
    }

    public void updateHashSuffix(int newHashSuffix,
                                 byte newHashSuffixDepth) {
      if (newHashSuffixDepth < 0 || newHashSuffixDepth > Integer.SIZE) {
        throw new IllegalArgumentException("hashSuffixDepth(=" + newHashSuffixDepth + ") must be in [0..32)");
      }
      int mask = ~suffixMask(newHashSuffixDepth);
      if ((newHashSuffix & mask) != 0) {
        throw new IllegalArgumentException(
          "hashSuffix(=" + Integer.toBinaryString(newHashSuffix) + ") " +
          "must have no more than " + newHashSuffixDepth + " trailing bits " +
          "(mask: " + Integer.toBinaryString(mask) + ")"
        );
      }
      segmentBuffer.put(HASH_SUFFIX_DEPTH_OFFSET, newHashSuffixDepth);
      segmentBuffer.putInt(HASH_SUFFIX_OFFSET, newHashSuffix);
    }

    @Override
    public int entriesCount() {
      return slotsCount() / 2;
    }

    /** entryIndex in [0..entriesCount) */
    @Override
    public int entryKey(int entryIndex) {
      return slot(entryIndex * 2);
    }

    /** entryIndex in [0..entriesCount) */
    @Override
    public int entryValue(int entryIndex) {
      return slot(entryIndex * 2 + 1);
    }

    @Override
    public void updateEntry(int entryIndex,
                            int key,
                            int value) {
      segmentBuffer.putInt(offsetOfSlot(entryIndex * 2), key);
      segmentBuffer.putInt(offsetOfSlot(entryIndex * 2 + 1), value);
    }

    /** slotNo in [0..slotsCount) */
    private int slot(int slotNo) {
      return segmentBuffer.getInt(offsetOfSlot(slotNo));
    }

    private static int offsetOfSlot(int slotNo) {
      return HASHTABLE_SLOTS_OFFSET + slotNo * Integer.BYTES;
    }

    private int slotsCount() {
      return (segmentSize - STATIC_HEADER_SIZE) / Integer.BYTES;
    }


    @Override
    public String toString() {
      return "HashMapSegmentLayout[segmentNo=" + segmentIndex + ", segmentSize=" + segmentSize + "]" +
             "[hashSuffix: " + hashSuffix() + ", depth: " + hashSuffixDepth() + "]" +
             "{" + aliveEntriesCount() + " alive entries of " + entriesCount() + "}";
    }

    public String dump() throws IOException {
      StringBuilder sb = new StringBuilder(
        "Segment[#" + segmentIndex + "][size: " + segmentSize + "b]" +
        "[hashSuffix: " + hashSuffix() + ", depth: " + hashSuffixDepth() + ", mask: " + Integer.toBinaryString(hashSuffixMask()) + "]" +
        "[entries: " + entriesCount() + ", alive: " + aliveEntriesCount() + "]\n"
      );
      for (int i = 0; i < entriesCount(); i++) {
        int key = entryKey(i);
        int value = entryValue(i);
        if (key != 0) {
          sb.append("\t[").append(i).append("]=(").append(key).append(", ").append(value).append(")\n");
        }
      }
      return sb.toString();
    }
  }

  public interface BufferSource {
    @NotNull
    ByteBuffer slice(long offsetInFile,
                     int length) throws IOException;

    /** == {@code slice(offsetInFile, 4).getInt()} */
    int getInt(long offsetInFile) throws IOException;
  }

  /** Abstracts data storage for open-addressing hash-table implementation */
  public interface HashTableData {
    /** entry = (key, value) pair */
    int entriesCount();

    int aliveEntriesCount();

    void updateAliveEntriesCount(int aliveCount);

    int entryKey(int index);

    int entryValue(int index);

    void updateEntry(int index,
                     int key,
                     int value);
  }

  private static final class HashMapAlgo {
    public static final int NO_VALUE = 0;

    private final float loadFactor;

    //MAYBE RC: load factor and probing algorithm choice: in-memory open-addressing hash tables usually employ
    // loadFactor=0.5 -- to prevent excessive clustering and probe sequence length increasing. For multi-valued
    // maps this may be even down to 0.4 due to more excessive clustering (see comments in Int2IntMultimap).
    // But for on-disk hash table it may worth to actually increase loadFactor up to 0.6-0.7 -- because less
    // IO due to more compact representation may easily outweigh more probing.
    // Tuning probing sequence could be also a thing: currently we use linear probing, since it is the
    // fastest, and also has spatial locality -- consequent probes are one-after-another, which is beneficial
    // given probing is done over IO-backed storage. But linear probing is also more susceptible to clustering,
    // and usually requires lower load-factors to prevent it.
    // We could try quadratic (i+i^2)/2, or exponential (https://dl.acm.org/doi/pdf/10.1145/264216.264221) probing,
    // there few first probes are still quite close to each other, i.e. most likely on the same (OS memory manager)
    // page, but following become more and more distant. Quadratic probing is known to alleviate clustering, and
    // allows higher load-factors => more dense representation, less IO.
    // ...But to start experimenting with that first we need a way to monitor entries/tombstones/alive, way to
    // sample avg/99%/max probing sequence length, etc.

    //Table entries convention:
    //  (key, value)                         = (table[2*i], table[2*i+1])
    //  (key: NO_VALUE, value: NO_VALUE)     = empty slot (not yet allocated)
    //  (key: NO_VALUE, value != NO_VALUE)   = 'tombstone', i.e. deleted slot (key-value pair was inserted and removed)


    private HashMapAlgo(float loadFactor) {
      this.loadFactor = loadFactor;
    }

    /** @return value for which valuesAcceptor returns true, else NO_VALUE */
    public int lookup(@NotNull HashTableData table,
                      int key,
                      ValueAcceptor valuesAcceptor) throws IOException {
      checkNotNoValue("key", key);
      int capacity = capacity(table);
      int startIndex = Math.abs(hash(key) % capacity);
      for (int probe = 0; probe < capacity; probe++) {
        int slotIndex = (startIndex + probe) % capacity;
        int slotKey = table.entryKey(slotIndex);
        int slotValue = table.entryValue(slotIndex);
        if (slotKey == key) {
          assert slotValue != NO_VALUE : "value(table[" + (slotIndex * 2 + 1) + "]) = " + NO_VALUE + " (NO_VALUE), " +
                                         "while key(table[" + slotIndex * 2 + "]) = " + key;
          if (valuesAcceptor.accept(slotValue)) {
            return slotValue;
          }
        }
        else if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
          //free slot -> end of probing sequence, no (key, value) found:
          break;
        }
      }
      return NO_VALUE;
    }

    public boolean has(@NotNull HashTableData table,
                       int key,
                       int value) {
      checkNotNoValue("key", key);
      checkNotNoValue("value", value);
      int capacity = capacity(table);
      int startIndex = Math.abs(hash(key) % capacity);
      for (int probe = 0; probe < capacity; probe++) {
        int slotIndex = (startIndex + probe) % capacity;
        int slotKey = table.entryKey(slotIndex);
        int slotValue = table.entryValue(slotIndex);
        if (slotKey == key && slotValue == value) {
          return true;
        }
        else if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
          //free slot -> end of probing sequence, no (key, value) found:
          break;
        }
      }
      return false;
    }


    public boolean put(@NotNull HashTableData table,
                       int key,
                       int value) {
      //MAYBE RC: check hash(key) has correct hashSuffix -- but this violates an abstraction of HashAlgo,
      //          so this check is better to be moved to HashSegment?
      checkNotNoValue("key", key);
      checkNotNoValue("value", value);

      int capacity = capacity(table);
      int startIndex = Math.abs(hash(key) % capacity);
      int firstTombstoneIndex = -1;
      for (int probe = 0; probe < capacity; probe++) {
        int slotIndex = (startIndex + probe) % capacity;
        int slotKey = table.entryKey(slotIndex);
        int slotValue = table.entryValue(slotIndex);
        if (slotKey == key && slotValue == value) {
          return false;//record already here, nothing to add
        }

        if (!isSlotOccupied(slotKey)) {
          if (slotValue != NO_VALUE) {
            //slot removed ('tombstone') -> remember the index (for later insertion), but continue lookup
            if (firstTombstoneIndex == -1) {
              firstTombstoneIndex = slotIndex;
            }
          }
          else {
            //(NO_VALUE, NO_VALUE) == free slot
            // => end of probing sequence, no (key, value) found
            // => insert it:
            int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : slotIndex;
            table.updateEntry(insertionIndex, key, value);
            incrementAliveValues(table);
            return true;
          }
        }
      }

      //probing sequence went through all the table: i.e. table is full -- but maybe there are tombstones to replace?

      //TODO RC: In the current design we never clear tombstones: during segment split we copy half of alive entries
      // to a new segment, but we leave the tombstones in old one -- hence, we never clean the tombstones in the old
      // segment. Tombstones are somewhat 'cleaned' by reusing their slots for new records, but this is stochastic,
      // and could be not very effective.
      // Hence, in the current design it could be there are not-so-many alive entries, but a lot of tombstones -- which
      // deteriorates performance.
      // The simplest solution would be to prune the tombstones on segment split in old segment also: currently we copy
      // ~1/2 alive entries in the new segment -- instead we should copy _all_ alive entries into memory buffer first,
      // clean the old segment entirely, and copy the entries from in-memory buffer into old/new segments afterwards.

      if (aliveValues(table) == 0) {
        //If there is 0 alive records => it is OK to clear all the tombstones.
        // We can't clear all tombstones while alive entries exist because such a cleaning breaks lookup: we treat
        // free slots and tombstones differently during the probing -- continue to probe over tombstones, but stop
        // on free slots. Converting tombstone to free slot could stop the probing earlier than it should stop, thus
        // making some existing entries unreachable.
        // But if there are no alive entries anymore -- we finally _can_ clear everything without breaking anything!

        //This deals with the issue above -- table being overflowed by tombstones -- but only partially. This branch
        // fixes correctness (table doesn't fail if there is at least 1 unfilled slot), but doesn't fix performance,
        // which likely is awful long before we reach this branch due to looooong probing sequences

        for (int slot = 0; slot < capacity; slot++) {
          table.updateEntry(slot, NO_VALUE, NO_VALUE);
        }
        return put(table, key, value);
      }

      if (firstTombstoneIndex != -1) {
        //Probing sequence is terminated if:
        // 1) key is found
        // 2) free slot is found
        // 3) no more slots to look for key -- all slots was visited (=key is definitely absent in the map)
        // So, if we scan all the entries and not found a key, but we have found a tombstone along the way
        //  => we can replace it with the entry at hand:
        table.updateEntry(firstTombstoneIndex, key, value);
        incrementAliveValues(table);
        return true;
      }

      //Table must be resized well before such a condition occurs!
      throw new AssertionError(
        "Table is full: all " + capacity + " items were traversed, but no free slot found, " +
        "table: " + table
      );
    }

    public boolean remove(@NotNull HashTableData table,
                          int key,
                          int value) {
      checkNotNoValue("key", key);
      checkNotNoValue("value", value);

      int capacity = capacity(table);
      int startIndex = Math.abs(hash(key) % capacity);
      for (int probe = 0; probe < capacity; probe++) {
        int slotIndex = (startIndex + probe) % capacity;
        int slotKey = table.entryKey(slotIndex);
        int slotValue = table.entryValue(slotIndex);
        if (slotKey == key && slotValue == value) {
          //reset key, but leave value as-is: this is the marker of 'removed' slot
          markEntryAsDeleted(table, slotIndex);
          //No need to look farther, since only one (key,value) record could be in the map
          return true;
        }
        if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
          //free slot -> end of probing sequence, no (key, value) found -> nothing to remove:
          return false;
        }
      }
      return false;
    }

    public boolean replace(@NotNull HashTableData table,
                           int key,
                           int oldValue,
                           int newValue) {
      checkNotNoValue("key", key);
      checkNotNoValue("oldValue", oldValue);
      checkNotNoValue("newValue", newValue);

      int capacity = capacity(table);
      int startIndex = Math.abs(hash(key) % capacity);
      //BEWARE: .replace() must maintain an invariant that key's values is a _set_ -- not just a list.
      // I.e. if newValue is already exist among the key's values -- oldValue should NOT be replaced, but just removed,
      // to not create 2 newValue entries => we need to look for both old & newValue first, and only then decide
      // how to behave:
      int oldValueSlotIndex = -1;
      int newValueSlotIndex = -1;
      for (int probe = 0; probe < capacity; probe++) {
        int slotIndex = (startIndex + probe) % capacity;
        int slotKey = table.entryKey(slotIndex);
        int slotValue = table.entryValue(slotIndex);
        if (slotKey == key) {
          if (slotValue == oldValue) {
            oldValueSlotIndex = slotIndex;
          }
          else if (slotValue == newValue) {
            newValueSlotIndex = slotIndex;
          }
        }
        if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
          //free slot -> end of probing sequence
          break;
        }
      }

      if (oldValueSlotIndex != -1) {
        if (newValueSlotIndex != -1) {
          //both oldValue and newValue exists in the map
          // => no need to update anything, just mark oldValue slot as 'deleted':
          markEntryAsDeleted(table, oldValueSlotIndex);
        }
        else {
          //newValue is not exists in key's values set
          // => update slot (old->new)Value:
          table.updateEntry(oldValueSlotIndex, key, newValue);
        }
        return true;
      }
      else {
        //oldValue is not exist -> do nothing
        return false;
      }
    }

    public boolean forEach(@NotNull HashTableData table,
                           @NotNull KeyValueProcessor processor) throws IOException {
      int capacity = capacity(table);
      for (int index = 0; index < capacity; index++) {
        int key = table.entryKey(index);
        int value = table.entryValue(index);
        if (isSlotOccupied(key)) {
          assert value != NO_VALUE : "value(table[" + (index + 1) + "]) = " + NO_VALUE + ", while key(table[" + index + "]) = " + key;
          if (!processor.process(key, value)) {
            return false;
          }
        }
      }
      return true;
    }

    public boolean isSlotOccupied(int key) {
      return key != NO_VALUE;
    }

    public int capacity(@NotNull HashTableData table) {
      return table.entriesCount();
    }

    public boolean needsSplit(@NotNull HashTableData table) {
      //MAYBE RC: could also use other triggers, e.g. average probing length...
      return aliveValues(table) > capacity(table) * loadFactor;
    }

    public int size(@NotNull HashTableData table) {
      return aliveValues(table);
    }

    public void markEntryAsDeleted(@NotNull HashTableData table,
                                   int entryIndex) {
      table.updateEntry(entryIndex, NO_VALUE, table.entryValue(entryIndex));
      decrementAliveValues(table);
    }

    // =========================== implementation: ======================================================

    private static int aliveValues(@NotNull HashTableData table) {
      return table.aliveEntriesCount();
    }

    private static void incrementAliveValues(@NotNull HashTableData table) {
      table.updateAliveEntriesCount(table.aliveEntriesCount() + 1);
    }

    private static void decrementAliveValues(@NotNull HashTableData table) {
      table.updateAliveEntriesCount(table.aliveEntriesCount() - 1);
    }

    private static void checkNotNoValue(String paramName,
                                        int value) {
      if (value == NO_VALUE) {
        throw new IllegalArgumentException(paramName + " can't be = " + NO_VALUE + " -- it is special value used as NO_VALUE");
      }
    }
  }

  private record BufferSourceOverMMappedFileStorage(@NotNull MMappedFileStorage storage) implements BufferSource {
    @Override
    public @NotNull ByteBuffer slice(long offsetInFile,
                                     int length) throws IOException {
      ByteBuffer buffer = storage.pageByOffset(offsetInFile).rawPageBuffer();
      int offsetInPage = storage.toOffsetInPage(offsetInFile);
      return buffer.slice(offsetInPage, length)
        .order(buffer.order());
    }

    @Override
    public int getInt(long offsetInFile) throws IOException {
      ByteBuffer buffer = storage.pageByOffset(offsetInFile).rawPageBuffer();
      int offsetInPage = storage.toOffsetInPage(offsetInFile);
      return buffer.getInt(offsetInPage);
    }

    @Override
    public String toString() {
      return "BufferSourceOverMMappedFileStorage{" + storage + '}';
    }
  }
}
