// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.IntToMultiIntMap;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MMappedFileStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

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
 * not concurrent
 */
public class ExtendibleHashmap implements IntToMultiIntMap {
  private static final int VERSION = 1;

  public static final int DEFAULT_SEGMENT_SIZE = 1 << 15; //32k

  //Binary layout: (header segment) (data segment)+
  //  Header segment: (fixed header fields) (segments table)
  //                  fixed header fields: 80 bytes
  //                                       versions, etc...
  //                                       segmentsTableSize: int32
  //                                       segmentsCount:     int32
  //                                       globalDepth:       int8
  //                  segments table: int16[segmentsTableSize], contains apt data segment index
  //  Data segment:  (fixed segment header) (hashtable data)
  //                 fixed segment header: 16 bytes
  //                                       segment depth: int8
  //                                       segment live entries count: int32
  //                 hashtable data: int32[N]
  //For now I plan making header and data segments same size -- this simplifies overall layout,
  // since we need to align everything to a page size, and it is much easier to align a fixed
  // size segments -- just need (pageSize % segmentSize = 0).
  //32K seems like a good segment size: that leaves us with (32k-80)/2 ~= 16k segments, each
  // segment contains (32k-16)/4/2 ~= 4k of (key:int32,value:int32) entries. Open addressing
  // hashmaps usually sized with loadFactory ~= 0.5, so 4k entries mean ~2k useful payload.
  // In total, ~16k segments of ~2k key-value pairs each gives us 32M entries max

  private final MMappedFileStorage storage;
  private transient BufferSource bufferSource;

  private transient HeaderLayout header;

  private final transient HashMapAlgo hashMapAlgo = new HashMapAlgo(0.5f);

  public ExtendibleHashmap(@NotNull MMappedFileStorage storage) throws IOException {
    this(storage, DEFAULT_SEGMENT_SIZE);
  }

  public ExtendibleHashmap(@NotNull MMappedFileStorage storage,
                           int segmentSize) throws IOException {
    if (Integer.bitCount(segmentSize) != 1) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be power of 2");
    }
    this.storage = storage;
    boolean fileIsEmpty = (storage.actualFileSize() == 0);

    synchronized (this) {
      bufferSource = (offsetInFile, length) -> {
        ByteBuffer buffer = storage.pageByOffset(offsetInFile).rawPageBuffer();
        int offsetInPage = storage.toOffsetInPage(offsetInFile);
        return buffer.slice(offsetInPage, length);
      };

      header = new HeaderLayout(bufferSource, segmentSize);
      if (!fileIsEmpty) {
        if (header.segmentSize() != segmentSize) {
          throw new IOException("segmentSize(=" + segmentSize + ") != storage segmentSize(=" + header.segmentSize() + ")");
        }
      }
      else {
        header.segmentSize(segmentSize);
        header.version(VERSION);
        header.globalHashSuffixDepth(0);
        header.actualSegmentsCount(0);
        HashMapSegmentLayout segment = allocateSegment(0, header.globalHashSuffixDepth());
        header.updateSegmentIndex(0, segment.segmentIndex());
      }
    }
  }

  @Override
  public synchronized int lookup(int key,
                                 @NotNull ValueAcceptor valuesAcceptor) throws IOException {
    int hash = hash(key);
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int segmentSlotIndex = segmentSlotIndex(hash, hashSuffixDepth);
    int segmentIndex = header.segmentIndex(segmentSlotIndex);
    HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());

    return hashMapAlgo.lookup(segment, key, valuesAcceptor);
  }

  @Override
  public synchronized boolean has(int key,
                                  int value) throws IOException {
    int hash = hash(key);
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int segmentSlotIndex = segmentSlotIndex(hash, hashSuffixDepth);
    int segmentIndex = header.segmentIndex(segmentSlotIndex);
    HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());

    return hashMapAlgo.has(segment, key, value);
  }

  @Override
  public synchronized int lookupOrInsert(int key,
                                         @NotNull ValueAcceptor valuesAcceptor,
                                         @NotNull ValueCreator valueCreator) throws IOException {
    int hash = hash(key);
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int segmentSlotIndex = segmentSlotIndex(hash, hashSuffixDepth);
    int segmentIndex = header.segmentIndex(segmentSlotIndex);
    HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());

    int valueFound = hashMapAlgo.lookup(segment, key, valuesAcceptor);
    if (valueFound == NO_VALUE) {
      int newValue = valueCreator.newValueForKey(key);
      hashMapAlgo.put(segment, key, newValue);
    }
    return valueFound;
  }

  @Override
  public synchronized void put(int key,
                               int value) throws IOException {
    int hash = hash(key);
    int segmentIndex = header.segmentIndexByHash(hash);
    HashMapSegmentLayout segment = new HashMapSegmentLayout(bufferSource, segmentIndex, header.segmentSize());

    hashMapAlgo.put(segment, key, value);

    if (hashMapAlgo.needsSplit(segment)) {
      splitAndRearrangeEntries(segment);
    }
  }


  @Override
  public synchronized void flush() throws IOException {
    //nothing to do
  }

  @Override
  public synchronized void close() throws IOException {
    storage.close();
    //clean all references to mapped ByteBuffers:
    header = null;
    bufferSource = null;
  }

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

    //There are 2^(globalDepth-segmentDepth) references to a segment with segmentDepth, and all them
    // have segmentSlotIndex with [segment.hashSuffix] -- this is how keys with segment.hashSuffix end
    // up in an apt segment. So we could generate all the segmentSlotIndexes this segment must be referred
    // from by simply iterating through 2^(globalDepth-segmentDepth) indexes with bit-suffix=segment.hashSuffix
    int segmentReferenceCount = 1 << (header.globalHashSuffixDepth() - newSegment.hashSuffixDepth());
    for (int i = 0; i < segmentReferenceCount; i++) {
      int segmentSlotIndex = (i << newSegment.hashSuffixDepth()) | newSegment.hashSuffix();
      int segmentIndex = header.segmentIndex(segmentSlotIndex);
      assert segmentIndex == oldSegment.segmentIndex()
        : "segment[" + segmentSlotIndex + "].segmentIndex(=" + segmentIndex + ") but it must be " + oldSegment.segmentIndex();

      header.updateSegmentIndex(segmentSlotIndex, newSegment.segmentIndex());
    }
  }

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
  private Pair<HashMapSegmentLayout, HashMapSegmentLayout> split(@NotNull HashMapSegmentLayout segmentToSplit) throws IOException {
    int oldHashSuffix = segmentToSplit.hashSuffix();
    byte oldHashSuffixDepth = segmentToSplit.hashSuffixDepth();

    byte newHashSuffixDepth = (byte)(oldHashSuffixDepth + 1);
    int highestSuffixBit = 1 << (newHashSuffixDepth - 1);
    int hashSuffix0 = oldHashSuffix;
    int hashSuffix1 = oldHashSuffix | highestSuffixBit;
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
    }

    return Pair.pair(segmentToSplit, newSegment);
  }

  /**
   * Doubles segments table size, and copies entries from the first half into a second (new) half.
   * E.g. table[1,2,3] after doubling become [1,2,3, 1,2,3]
   */
  private void doubleSegmentsTable() throws IOException {
    int hashSuffixDepth = header.globalHashSuffixDepth();
    int oldTableSize = header.segmentTableSize();
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


  //=============== implementation ============================================================

  static final class HeaderLayout {
    //@formatter:off
    private static final int VERSION_OFFSET                  = 0;   //int32
    private static final int SEGMENT_SIZE_OFFSET             = 4;   //int32
    private static final int ACTUAL_SEGMENTS_COUNT_OFFSET    = 8;   //int32
    private static final int GLOBAL_HASH_SUFFIX_DEPTH_OFFSET = 12;  //int8
    // region [13..79] is reserved for generations to come
    private static final int STATIC_HEADER_SIZE              = 80;

    private static final int SEGMENTS_TABLE_OFFSET = STATIC_HEADER_SIZE; //int16[N]
    //@formatter:on

    private final int headerSize;
    private final ByteBuffer headerBuffer;

    HeaderLayout(@NotNull BufferSource bufferSource,
                 int headerSize) throws IOException {
      if (headerSize <= STATIC_HEADER_SIZE) {
        throw new IllegalArgumentException("headerSize(=" + headerSize + ") must be > STATIC_HEADER_SIZE(=" + STATIC_HEADER_SIZE + ")");
      }
      this.headerSize = headerSize;//must be ==segmentSize
      headerBuffer = bufferSource.slice(0, headerSize);
    }

    public int version() throws IOException {
      return headerBuffer.getInt(VERSION_OFFSET);
    }

    public void version(int version) throws IOException {
      headerBuffer.putInt(VERSION_OFFSET, version);
    }


    public int segmentSize() throws IOException {
      return headerBuffer.getInt(SEGMENT_SIZE_OFFSET);
    }

    public void segmentSize(int size) {
      headerBuffer.putInt(SEGMENT_SIZE_OFFSET, size);
    }


    public int actualSegmentsCount() throws IOException {
      return headerBuffer.getInt(ACTUAL_SEGMENTS_COUNT_OFFSET);
    }

    public void actualSegmentsCount(int count) throws IOException {
      headerBuffer.putInt(ACTUAL_SEGMENTS_COUNT_OFFSET, count);
    }

    /** How many trailing bits of key.hash to use to determine segment to store the key */
    public byte globalHashSuffixDepth() throws IOException {
      return headerBuffer.get(GLOBAL_HASH_SUFFIX_DEPTH_OFFSET);
    }

    public void globalHashSuffixDepth(int depth) throws IOException {
      if (depth < 0 || depth >= Integer.SIZE) {
        throw new IllegalArgumentException("depth(=" + depth + ") must be in [0..32)");
      }
      headerBuffer.put(GLOBAL_HASH_SUFFIX_DEPTH_OFFSET, (byte)depth);
    }

    /** @return segmentIndex in [1..actualSegmentsCount], for slotIndex in [0..segmentTableSize) */
    public int segmentIndex(int slotIndex) throws IOException {
      Objects.checkIndex(slotIndex, segmentTableSize());
      return headerBuffer.getShort(SEGMENTS_TABLE_OFFSET + slotIndex * Short.BYTES);
    }

    public int segmentIndexByHash(int hash) throws IOException {
      int hashSuffixDepth = globalHashSuffixDepth();
      int segmentSlotIndex = segmentSlotIndex(hash, hashSuffixDepth);
      return segmentIndex(segmentSlotIndex);
    }

    public int segmentTableSize() throws IOException {
      return 1 << globalHashSuffixDepth();
    }

    public void updateSegmentIndex(int segmentSlotIndex,
                                   int segmentIndex) throws IOException {
      Objects.checkIndex(segmentSlotIndex, segmentTableSize());
      if (segmentIndex < 1 || segmentIndex >= Short.MAX_VALUE) {
        throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be in [1..MAX_SHORT)");
      }
      headerBuffer.putShort(SEGMENTS_TABLE_OFFSET + segmentSlotIndex * Short.BYTES, (short)segmentIndex);
    }

    public int headerSize() { return headerSize; }

    @Override
    public String toString() {
      return "HeaderLayout[headerSize=" + headerSize + ']';
    }

    public String dump() throws IOException {
      StringBuilder sb =
        new StringBuilder("HeaderLayout[size: " + headerSize + "b, globalHashSuffixSize: " + globalHashSuffixDepth() + "]");
      sb.append("[tableSize: " + segmentTableSize() + ", actualSegments: " + actualSegmentsCount() + "]\n");
      for (int i = 0; i < segmentTableSize(); i++) {
        sb.append("\t[" + i + "]=" + segmentIndex(i) + "\n");
      }
      return sb.toString();
    }
  }

  @VisibleForTesting
  static final class HashMapSegmentLayout implements HashTableData {
    //@formatter:off
    private static final int LIVE_ENTRIES_COUNT_OFFSET  =  0; //int32
    private static final int HASH_SUFFIX_OFFSET         =  4; //int32
    private static final int HASH_SUFFIX_DEPTH_OFFSET   =  8; //int8
    // == region [9..15] is reserved for generations to come
    private static final int STATIC_HEADER_SIZE         = 16;

    private static final int HASHTABLE_SLOTS_OFFSET     = STATIC_HEADER_SIZE; //int32[N]
    //@formatter:on

    private final int segmentSize;
    private final int segmentIndex;

    private final long offsetInFile;
    private final ByteBuffer segmentBuffer;

    HashMapSegmentLayout(@NotNull BufferSource bufferSource,
                         int segmentIndex,
                         int segmentSize) throws IOException {
      if (segmentIndex < 1) {
        throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be >=1 (0-th segment is a header)");
      }
      this.segmentIndex = segmentIndex;
      this.segmentSize = segmentSize;

      long offsetInFile = segmentIndex * (long)segmentSize;

      this.offsetInFile = offsetInFile;
      this.segmentBuffer = bufferSource.slice(offsetInFile, segmentSize);
    }

    public int segmentIndex() { return segmentIndex; }

    public int segmentSize() { return segmentSize; }

    @Override
    public int aliveEntriesCount() {
      return segmentBuffer.getInt(LIVE_ENTRIES_COUNT_OFFSET);
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
             "[hashSuffix: " + hashSuffix() + ", depth: " + hashSuffixDepth() + "]";
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
          sb.append("\t[" + i + "]=(" + key + ", " + value + ")\n");
        }
      }
      return sb.toString();
    }
  }

  @FunctionalInterface
  interface BufferSource {
    @NotNull ByteBuffer slice(long offsetInFile,
                              int length) throws IOException;
  }

  /** Abstracts data storage for open-addressing hash-table implementation */
  interface HashTableData {
    int entriesCount();

    int aliveEntriesCount();

    void updateAliveEntriesCount(int aliveCount);

    int entryKey(int index);

    int entryValue(int index);

    void updateEntry(int index,
                     int key,
                     int value);
  }

  public static final class HashMapAlgo {
    public static final int NO_VALUE = 0;

    private final float loadFactor;

    //Table entries convention:
    //  (key, value)                         = (table[2*i], table[2*i+1])
    //  (key: NO_VALUE, value: NO_VALUE)     = empty slot (not yet allocated)
    //  (key: NO_VALUE, value != NO_VALUE)   = 'tombstone', i.e. deleted slot (key-value pair was inserted and removed)
    //


    public HashMapAlgo(float loadFactor) {
      this.loadFactor = loadFactor;
    }

    /**
     * @return true if iterated through all values, false if iteration was stopped early by valuesProcessor returning false
     */
    public int lookup(@NotNull HashTableData table,
                      int key,
                      ValueAcceptor valuesAcceptor) throws IOException {
      checkNotNoValue("key", key);
      int capacity = table.entriesCount();
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
      //FIXME RC: check hash(key) has correct hashSuffix -- but this violates an abstraction of HashAlgo,
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
            //slot removed ('tombstone')-> remember index, but continue lookup
            if (firstTombstoneIndex == -1) {
              firstTombstoneIndex = slotIndex;
            }
          }
          else {
            //(NO_VALUE, NO_VALUE) -> free slot -> end of probing sequence, no (key, value) found -> insert it:
            int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : slotIndex;
            table.updateEntry(insertionIndex, key, value);
            incrementAliveValues(table);
            break;
          }
        }
      }

      return true;
    }

    public void remove(@NotNull HashTableData table,
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
          table.updateEntry(slotIndex, NO_VALUE, value);
          decrementAliveValues(table);
          //No need to look farther, since only one (key,value) record could be in the map
          return;
        }
        if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
          //free slot -> end of probing sequence, no (key, value) found -> nothing to remove:
          return;
        }
      }
    }

    public void forEach(@NotNull HashTableData table,
                        KeyValueProcessor processor) {
      int capacity = capacity(table);
      for (int index = 0; index < capacity; index++) {
        final int key = table.entryKey(index);
        final int value = table.entryValue(index);
        if (isSlotOccupied(key)) {
          assert value != NO_VALUE : "value(table[" + (index + 1) + "]) = " + NO_VALUE + ", while key(table[" + index + "]) = " + key;
          if (!processor.process(key, value)) {
            return;
          }
        }
      }
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

    private static int aliveValues(@NotNull HashTableData table) {
      return table.aliveEntriesCount();
    }

    private static void incrementAliveValues(@NotNull HashTableData table) {
      table.updateAliveEntriesCount(table.aliveEntriesCount() + 1);
    }

    private static void decrementAliveValues(@NotNull HashTableData table) {
      table.updateAliveEntriesCount(table.aliveEntriesCount() - 1);
    }

    @FunctionalInterface
    public interface KeyValueProcessor {
      boolean process(final int key,
                      final int value);
    }

    private static void checkNotNoValue(final String paramName,
                                        final int value) {
      if (value == NO_VALUE) {
        throw new IllegalArgumentException(paramName + " can't be = " + NO_VALUE + " -- it is special value used as NO_VALUE");
      }
    }
  }
}
