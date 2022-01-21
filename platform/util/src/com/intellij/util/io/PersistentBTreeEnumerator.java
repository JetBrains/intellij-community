// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.keyStorage.AppendableObjectStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Assigns / store unique integral id for Data instances.
// Btree stores mapping between integer hash code into integer that interpreted in following way:
// Positive value is address in myFile with unique key record.
// When there is hash value collisions the value is negative and it is -address of collision list (keyAddress, nextCollisionAddress)+
// It is possible to directly associate non-negative int or long with Data instances when Data is integral value and represent it's own hash code
// e.g. Data are integers and hash code for them are values themselves
public class PersistentBTreeEnumerator<Data> extends PersistentEnumeratorBase<Data> {
  private static final int BTREE_PAGE_SIZE;
  private static final int DEFAULT_BTREE_PAGE_SIZE = 32768;

  private static final boolean DO_EXPENSIVE_CHECKS = SystemProperties.getBooleanProperty("idea.persistent.enumerator.do.expensive.checks", false);
  @VisibleForTesting
  public static final String DO_SELF_HEAL_PROP = "idea.persistent.enumerator.do.self.heal";

  static {
    BTREE_PAGE_SIZE = SystemProperties.getIntProperty("idea.btree.page.size", DEFAULT_BTREE_PAGE_SIZE);
  }

  private static final int RECORD_SIZE = 4;
  private static final int VALUE_PAGE_SIZE = 1024 * 1024;

  static {
    assert VALUE_PAGE_SIZE % BTREE_PAGE_SIZE == 0 : "Page size should be divisor of " + VALUE_PAGE_SIZE;
  }

  private static final int INTERNAL_PAGE_SIZE = ResizeableMappedFile.DEFAULT_ALLOCATION_ROUND_FACTOR;

  private int myLogicalFileLength;
  private int myDataPageStart;
  private int myFirstPageStart;

  private int myDataPageOffset;
  private int myDuplicatedValuesPageStart;
  private int myDuplicatedValuesPageOffset;
  private static final int COLLISION_OFFSET = 4;
  private int myValuesCount;
  private int myCollisions;
  private int myExistingKeysEnumerated;

  private IntToIntBtree myBTree;
  private final boolean myInlineKeysNoMapping;
  private boolean myExternalKeysNoMapping;

  private final @Nullable PersistentEnumeratorWal<Data> myWal;

  private static final int MAX_DATA_SEGMENT_LENGTH = 128;

  protected static int baseVersion() {
    return 8 + IntToIntBtree.version() + BTREE_PAGE_SIZE + INTERNAL_PAGE_SIZE + MAX_DATA_SEGMENT_LENGTH;
  }

  private static final int KEY_SHIFT = 1;

  public PersistentBTreeEnumerator(@NotNull Path file, @NotNull KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentBTreeEnumerator(@NotNull Path file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable StorageLockContext lockContext) throws IOException {
    this(file, dataDescriptor, initialSize, lockContext, 0, false);
  }

  public PersistentBTreeEnumerator(@NotNull Path file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable StorageLockContext lockContext,
                                   int version) throws IOException {
    this(file, dataDescriptor, initialSize, lockContext, version, false);
  }

  public PersistentBTreeEnumerator(@NotNull Path file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable StorageLockContext lockContext,
                                   int version,
                                   boolean enableWal) throws IOException {
    super(file,
          new ResizeableMappedFile(
            file,
            initialSize,
            lockContext,
            VALUE_PAGE_SIZE,
            true,
            IOUtil.BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER
          ),
          dataDescriptor,
          initialSize,
          new Version(baseVersion() + version),
          new RecordBufferHandler(),
          false
    );

    myInlineKeysNoMapping = dataDescriptor instanceof InlineKeyDescriptor;
    myExternalKeysNoMapping = !(dataDescriptor instanceof InlineKeyDescriptor);

    if (myBTree == null) {
      try {
        lockStorageWrite();
        storeVars(false);
        initBtree(false);
        storeBTreeVars(false);
      }
      catch (IOException e) {
        try {
          close();  // cleanup already initialized state
        }
        catch (Throwable ignored) {
        }
        throw e;
      }
      catch (Throwable e) {
        LOG.info(e);
        try {
          close();  // cleanup already initialized state
        }
        catch (Throwable ignored) {
        }
        throw new CorruptedException(file);
      }
      finally {
        unlockStorageWrite();
      }
    }

    diagnose();

    myWal = enableWal ? new PersistentEnumeratorWal<>(dataDescriptor,
                                                      false,
                                                      file.resolveSibling(file.getFileName() + ".wal"),
                                                      ConcurrencyUtil.newSameThreadExecutorService(),
                                                      true) : null;
  }

  private void doExpensiveSanityCheck() {
    try {
      LOG.info("Doing self diagnostic for " + myFile);
      List<Data> storedData = new ArrayList<>();
      iterateData(data -> {
        storedData.add(data);
        return true;
      });

      for (int i = 0; i < storedData.size(); i++) {
        try {
          Data data = storedData.get(i);
          int id = i + 1;
          if (tryEnumerate(data) != id) {
            throw new IOException(myFile + " is corrupted");
          }
          if (!myDataDescriptor.isEqual(valueOf(id), data)) {
            throw new IOException(myFile + " is corrupted");
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @Override
  public void diagnose() {
    if (DO_EXPENSIVE_CHECKS && !myInlineKeysNoMapping) {
      doExpensiveSanityCheck();
    }
  }

  @Override
  protected boolean trySelfHeal() {
    if (!SystemProperties.getBooleanProperty(DO_SELF_HEAL_PROP, false)) {
      return false;
    }
    LOG.info("Trying to self-heal " + myFile);

    class DataWithOffset {
      final Data data;
      final int offset;

      DataWithOffset(Data data, int offset) {
        this.data = data;
        this.offset = offset;
      }
    }

    List<DataWithOffset> items = new ArrayList<>();
    try {
      doIterateData((offset, data) -> {
        items.add(new DataWithOffset(data, offset));
        return true;
      });

      lockStorageWrite();
      try {
        myStorage.clear();
        myKeyStorage.clear();
        myStorage.ensureSize(4096);
        markDirty(true);
        putMetaData(0);
        putMetaData2(0);

        if (myBTree != null) {
          myBTree.doClose();
        }
        setupEmptyFile();
        doFlush();
      }
      finally {
        unlockStorageWrite();
      }

      for (DataWithOffset item : items) {
        int id = enumerate(item.data);
        int expectedId = item.offset + 1;
        if (expectedId != id) {
          throw new IOException("Enumeration order has been changed while self-healing, were " + expectedId + " now " + id);
        }
      }
    }
    catch (Throwable throwable) {
      LOG.info(throwable);
      return false;
    }
    return true;
  }

  @NotNull
  private static Path indexFile(@NotNull Path file) {
    return file.resolveSibling(file.getFileName() + "_i");
  }

  private void initBtree(boolean initial) throws IOException {
    myBTree = new IntToIntBtree(BTREE_PAGE_SIZE, indexFile(myFile), myStorage.getStorageLockContext(), initial);
  }

  private void storeVars(boolean toDisk) throws IOException {
    myLogicalFileLength = store(DATA_START, myLogicalFileLength, toDisk);
    myDataPageStart = store(DATA_START + 4, myDataPageStart, toDisk);
    myDataPageOffset = store(DATA_START + 8, myDataPageOffset, toDisk);
    myFirstPageStart = store(DATA_START + 12, myFirstPageStart, toDisk);
    myDuplicatedValuesPageStart = store(DATA_START + 16, myDuplicatedValuesPageStart, toDisk);
    myDuplicatedValuesPageOffset = store(DATA_START + 20, myDuplicatedValuesPageOffset, toDisk);
    myValuesCount = store(DATA_START + 24, myValuesCount, toDisk);
    myCollisions = store(DATA_START + 28, myCollisions, toDisk);
    myExistingKeysEnumerated = store(DATA_START + 32, myExistingKeysEnumerated, toDisk);
    storeBTreeVars(toDisk);
  }

  private void storeBTreeVars(boolean toDisk) throws IOException {
    final IntToIntBtree tree = myBTree;
    if (tree != null) {
      final int BTREE_DATA_START = DATA_START + 36;
      tree.persistVars(new IntToIntBtree.BtreeDataStorage() {
        @Override
        public int persistInt(int offset, int value, boolean toDisk) throws IOException {
          return store(BTREE_DATA_START + offset, value, toDisk);
        }
      }, toDisk);
    }
  }

  private int store(int offset, int value, boolean toDisk) throws IOException {
    assert offset + 4 < MAX_DATA_SEGMENT_LENGTH;

    if (toDisk) {
      if (myFirstPageStart == -1 || myStorage.getInt(offset) != value) myStorage.putInt(offset, value);
    }
    else {
      value = myStorage.getInt(offset);
    }
    return value;
  }

  @Override
  protected void setupEmptyFile() throws IOException {
    myLogicalFileLength = MAX_DATA_SEGMENT_LENGTH;
    myFirstPageStart = myDataPageStart = -1;
    myDuplicatedValuesPageStart = -1;

    initBtree(true);
    storeVars(true);
  }

  @Override
  protected void doClose() throws IOException {
    try {
      super.doClose();
    }
    finally {
      final IntToIntBtree tree = myBTree;
      if (tree != null) {
        tree.doClose();
      }
    }
  }

  private int allocPage() {
    int pageStart = myLogicalFileLength;
    myLogicalFileLength += INTERNAL_PAGE_SIZE - pageStart % INTERNAL_PAGE_SIZE;
    return pageStart;
  }

  @Override
  public boolean processAllDataObject(@NotNull final Processor<? super Data> processor, @Nullable final DataFilter filter)
    throws IOException {
    if (myInlineKeysNoMapping) {
      return traverseAllRecords(new RecordsProcessor() {
        @Override
        public boolean process(final int record) throws IOException {
          if (filter == null || filter.accept(record)) {
            Data data = ((InlineKeyDescriptor<Data>)myDataDescriptor).fromInt(getCurrentKey());
            return processor.process(data);
          }
          return true;
        }
      });
    }
    return super.processAllDataObject(processor, filter);
  }

  @Override
  public boolean traverseAllRecords(@NotNull final RecordsProcessor p) throws IOException {
    getReadLock().lock();
    try {
      lockStorageRead();
      try {
        return myBTree.processMappings(new IntToIntBtree.KeyValueProcessor() {
          @Override
          public boolean process(int key, int value) throws IOException {
            p.setCurrentKey(key);

            if (value > 0 || myInlineKeysNoMapping) {
              return p.process(value);
            }
            int rec = -value;
            while (rec != 0) {
              int id = myStorage.getInt(rec);
              if (!p.process(id)) return false;
              rec = myStorage.getInt(rec + COLLISION_OFFSET);
            }
            return true;
          }
        });
      }
      finally {
        unlockStorageRead();
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  protected int addrToIndex(int addr) {
    assert myExternalKeysNoMapping;
    return addr + KEY_SHIFT;
  }

  @Override
  protected int indexToAddr(int idx) throws IOException {
    if (myExternalKeysNoMapping) {
      IntToIntBtree.myAssert(idx > 0);
      return idx - KEY_SHIFT;
    }

    int anInt = myStorage.getInt(idx);
    if (IntToIntBtree.doSanityCheck) {
      IntToIntBtree.myAssert(anInt >= 0 || myDataDescriptor instanceof InlineKeyDescriptor);
    }
    return anInt;
  }

  @Override
  protected int setupValueId(int hashCode, int dataOff) throws IOException {
    if (myExternalKeysNoMapping) return addrToIndex(dataOff);
    final PersistentEnumeratorBase.@NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> recordHandler = getRecordHandler();
    final byte[] buf = recordHandler.getRecordBuffer(this);

    // optimization for using putInt / getInt on aligned empty storage (our page always contains on storage ByteBuffer)
    final int pos = recordHandler.recordWriteOffset(this, buf);
    myStorage.ensureSize(pos + buf.length);

    if (!myInlineKeysNoMapping) myStorage.putInt(pos, dataOff);
    return pos;
  }

  @Override
  public void setRecordHandler(@NotNull PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase<?>> recordHandler) {
    myExternalKeysNoMapping = false;
    super.setRecordHandler(recordHandler);
  }

  @Override
  public Data getValue(int keyId, int processingKey) throws IOException {
    if (myInlineKeysNoMapping) {
      return ((InlineKeyDescriptor<Data>)myDataDescriptor).fromInt(processingKey);
    }
    return super.getValue(keyId, processingKey);
  }

  private final int[] myResultBuf = new int[1];

  long getNonNegativeValue(Data key) throws IOException {
    getReadLock().lock();
    try {
      assert myInlineKeysNoMapping;
      try {
        lockStorageRead();
        final boolean hasMapping = myBTree.get(((InlineKeyDescriptor<Data>)myDataDescriptor).toInt(key), myResultBuf);
        if (!hasMapping) {
          return NULL_ID;
        }

        return keyIdToNonNegativeOffset(myResultBuf[0]);
      }
      finally {
        unlockStorageRead();
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  long keyIdToNonNegativeOffset(int value) throws IOException {
    if (value >= 0) return value;
    return myStorage.getLong(-value);
  }

  void putNonNegativeValue(Data key, long value) throws IOException {
    getWriteLock().lock();
    try {
      assert value >= 0;
      assert myInlineKeysNoMapping;
      try {
        lockStorageWrite();

        int intKey = ((InlineKeyDescriptor<Data>)myDataDescriptor).toInt(key);

        markDirty(true);

        if (value < Integer.MAX_VALUE) {
          myBTree.put(intKey, (int)value);
        }
        else {
          // reuse long record if it was allocated
          boolean hasMapping = myBTree.get(intKey, myResultBuf);
          if (hasMapping) {
            if (myResultBuf[0] < 0) {
              myStorage.putLong(-myResultBuf[0], value);
              return;
            }
          }

          int pos = nextLongValueRecord();
          myStorage.putLong(pos, value);
          myBTree.put(intKey, -pos);
        }
      }
      finally {
        unlockStorageWrite();
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

  private int nextLongValueRecord() {
    assert myInlineKeysNoMapping;
    if (myDuplicatedValuesPageStart == -1 || myDuplicatedValuesPageOffset == INTERNAL_PAGE_SIZE) {
      myDuplicatedValuesPageStart = allocPage();
      int existingOffset = myDuplicatedValuesPageStart % INTERNAL_PAGE_SIZE;
      myDuplicatedValuesPageOffset = existingOffset;
      myDuplicatedValuesPageStart -= existingOffset;
    }

    int duplicatedValueOff = myDuplicatedValuesPageOffset;
    myDuplicatedValuesPageOffset += 8; // size of long
    return myDuplicatedValuesPageStart + duplicatedValueOff;
  }

  @Override
  protected int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    (onlyCheckForExisting ? getReadLock() : getWriteLock()).lock();
    try {
      if (onlyCheckForExisting) {
        lockStorageRead();
      }
      else {
        lockStorageWrite();
      }

      try {
        if (IntToIntBtree.doDump) System.out.println(value);
        final int valueHC = myDataDescriptor.getHashCode(value);

        final boolean hasMapping = myBTree.get(valueHC, myResultBuf);
        if (!hasMapping && onlyCheckForExisting) {
          return NULL_ID;
        }

        final int indexNodeValueAddress = hasMapping ? myResultBuf[0] : 0;
        int collisionAddress = NULL_ID;
        boolean hasExistingData = false;

        if (!myInlineKeysNoMapping) {
          if (indexNodeValueAddress > 0) {
            // we found reference to no dupe key
            if (isKeyAtIndex(value, indexNodeValueAddress)) {
              if (!saveNewValue) {
                ++myExistingKeysEnumerated;
                return indexNodeValueAddress;
              }
              hasExistingData = true;
            }

            collisionAddress = indexNodeValueAddress;
          }
          else if (indexNodeValueAddress < 0) { // indexNodeValueAddress points to duplicates list
            collisionAddress = -indexNodeValueAddress;

            while (true) {
              final int address = myStorage.getInt(collisionAddress);
              if (isKeyAtIndex(value, address)) {
                if (!saveNewValue) return address;
                hasExistingData = true;
                break;
              }

              int newCollisionAddress = myStorage.getInt(collisionAddress + COLLISION_OFFSET);
              if (newCollisionAddress == 0) break;
              collisionAddress = newCollisionAddress;
            }
          }

          if (onlyCheckForExisting) return NULL_ID;
        }
        else {
          if (hasMapping) {
            if (!saveNewValue) return indexNodeValueAddress;
            hasExistingData = true;
          }
        }

        assert !onlyCheckForExisting;
        int newValueId = writeData(value, valueHC);
        ++myValuesCount;
        if (myWal != null) {
          myWal.enumerate(value, newValueId);
        }

        if (IOStatistics.DEBUG && (myValuesCount & IOStatistics.KEYS_FACTOR_MASK) == 0) {
          IOStatistics.dump("Index " +
                            myFile +
                            ", values " +
                            myValuesCount +
                            ", existing keys enumerated:" + myExistingKeysEnumerated +
                            ", storage size:" +
                            myStorage.length());
          myBTree.dumpStatistics();
        }

        if (collisionAddress != NULL_ID) {
          if (hasExistingData) {
            if (indexNodeValueAddress > 0) {
              myBTree.put(valueHC, newValueId);
            }
            else {
              myStorage.putInt(collisionAddress, newValueId);
            }
          }
          else {
            if (indexNodeValueAddress > 0) {
              // organize collision type reference
              int duplicatedValueOff = nextDuplicatedValueRecord();
              myBTree.put(valueHC, -duplicatedValueOff);

              myStorage.putInt(duplicatedValueOff, indexNodeValueAddress); // we will set collision offset in next if
              collisionAddress = duplicatedValueOff;
              ++myCollisions;
            }

            ++myCollisions;
            int duplicatedValueOff = nextDuplicatedValueRecord();
            myStorage.putInt(collisionAddress + COLLISION_OFFSET, duplicatedValueOff);
            myStorage.putInt(duplicatedValueOff, newValueId);
            myStorage.putInt(duplicatedValueOff + COLLISION_OFFSET, 0);
          }
        }
        else {
          myBTree.put(valueHC, newValueId);
        }

        if (IntToIntBtree.doSanityCheck) {
          if (!myInlineKeysNoMapping) {
            Data data = valueOf(newValueId);
            IntToIntBtree.myAssert(myDataDescriptor.isEqual(value, data));
          }
        }
        return newValueId;
      }
      finally {
        if (onlyCheckForExisting) {
          unlockStorageRead();
        }
        else {
          unlockStorageWrite();
        }
      }
    }
    finally {
      (onlyCheckForExisting ? getReadLock() : getWriteLock()).unlock();
    }
  }

  @Override
  public void force() {
    try {
      super.force();
    }
    finally {
      if (myWal != null) {
        myWal.flush();
      }
    }
  }

  @Override
  protected void dumpKeysOnCorruption() {
    try {
      force();
    }
    catch (Exception e) {
      // ignore...
    }

    lockStorageWrite();
    try {
      try {
        LOG.info("Listing corrupted enumerator:");
        doIterateData((offset, data) -> {
          LOG.info("Enumerator entry '" + data.toString() + "'");
          return true;
        });
        LOG.info("Listing ended.");
      }
      catch (Throwable throwable) {
        LOG.info(throwable);
      }

    }
    finally {
      unlockStorageWrite();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      super.close();
    }
    finally {
      if (myWal != null) {
        myWal.close();
      }
    }
  }

  @Override
  boolean canReEnumerate() {
    return true;
  }

  @Override
  public Data valueOf(int idx) throws IOException {
    assert !myInlineKeysNoMapping : "No valueOf for inline keys with no mapping option";
    return super.valueOf(idx);
  }

  private int nextDuplicatedValueRecord() {
    assert !myInlineKeysNoMapping;
    if (myDuplicatedValuesPageStart == -1 || myDuplicatedValuesPageOffset == INTERNAL_PAGE_SIZE) {
      myDuplicatedValuesPageStart = allocPage();
      int existingOffset = myDuplicatedValuesPageStart % INTERNAL_PAGE_SIZE;
      myDuplicatedValuesPageOffset = existingOffset;
      myDuplicatedValuesPageStart -= existingOffset;
    }

    int duplicatedValueOff = myDuplicatedValuesPageOffset;
    myDuplicatedValuesPageOffset += COLLISION_OFFSET + 4;
    return myDuplicatedValuesPageStart + duplicatedValueOff;
  }

  @Override
  protected void doFlush() throws IOException {
    myBTree.doFlush();
    storeVars(true);
    super.doFlush();
  }

  private static class RecordBufferHandler extends PersistentEnumeratorBase.RecordBufferHandler<PersistentBTreeEnumerator<?>> {
    private ThreadLocal<byte[]> myBuffer;

    @Override
    int recordWriteOffset(@NotNull PersistentBTreeEnumerator<?> enumerator, byte @NotNull [] buf) throws IOException {
      if (enumerator.myFirstPageStart == -1) {
        enumerator.myFirstPageStart = enumerator.myDataPageStart = enumerator.allocPage();
        int existingOffset = enumerator.myDataPageStart % INTERNAL_PAGE_SIZE;
        enumerator.myDataPageOffset = existingOffset;
        enumerator.myDataPageStart -= existingOffset;
      }
      if (enumerator.myDataPageOffset + buf.length + 4 > INTERNAL_PAGE_SIZE) {
        assert enumerator.myDataPageOffset + 4 <= INTERNAL_PAGE_SIZE;
        int prevDataPageStart = enumerator.myDataPageStart + INTERNAL_PAGE_SIZE - 4;
        enumerator.myDataPageStart = enumerator.allocPage();
        enumerator.myStorage.putInt(prevDataPageStart, enumerator.myDataPageStart);
        enumerator.myDataPageOffset = 0;
      }

      int recordWriteOffset = enumerator.myDataPageOffset;
      assert recordWriteOffset + buf.length + 4 <= INTERNAL_PAGE_SIZE;
      enumerator.myDataPageOffset += buf.length;
      return recordWriteOffset + enumerator.myDataPageStart;
    }

    @Override
    byte @NotNull [] getRecordBuffer(@NotNull PersistentBTreeEnumerator<?> enumerator) {
      if (myBuffer == null) {
        myBuffer = ThreadLocal.withInitial(() -> enumerator.myInlineKeysNoMapping ? ArrayUtilRt.EMPTY_BYTE_ARRAY : new byte[RECORD_SIZE]);
      }
      return myBuffer.get();
    }

    @Override
    void setupRecord(@NotNull PersistentBTreeEnumerator<?> enumerator, int hashCode, int dataOffset, byte[] buf) {
      if (!enumerator.myInlineKeysNoMapping) Bits.putInt(buf, 0, dataOffset);
    }
  }
}
