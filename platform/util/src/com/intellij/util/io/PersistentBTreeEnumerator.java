// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

// Assigns / store unique integral id for Data instances.
// Btree stores mapping between integer hash code into integer that interpreted in following way:
// Positive value is address in myFile with unique key record.
// When there is hash value collisions the value is negative and it is -address of collision list (keyAddress, nextCollisionAddress)+
// It is possible to directly associate non-negative int or long with Data instances when Data is integral value and represent it's own hash code
// e.g. Data are integers and hash code for them are values themselves
public class PersistentBTreeEnumerator<Data> extends PersistentEnumeratorBase<Data> {
  private static final int BTREE_PAGE_SIZE;
  private static final int DEFAULT_BTREE_PAGE_SIZE = 32768;

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

  private static final int MAX_DATA_SEGMENT_LENGTH = 128;

  protected static final int VERSION = 8 + IntToIntBtree.version() + BTREE_PAGE_SIZE + INTERNAL_PAGE_SIZE + MAX_DATA_SEGMENT_LENGTH;
  private static final int KEY_SHIFT = 1;

  public PersistentBTreeEnumerator(@NotNull Path file, @NotNull KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentBTreeEnumerator(@NotNull Path file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
    this(file, dataDescriptor, initialSize, lockContext, 0);
  }

  public PersistentBTreeEnumerator(@NotNull Path file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable PagedFileStorage.StorageLockContext lockContext,
                                   int version) throws IOException {
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
          new Version(VERSION + version),
          new RecordBufferHandler(),
          false
    );

    myInlineKeysNoMapping = dataDescriptor instanceof InlineKeyDescriptor;
    myExternalKeysNoMapping = !(dataDescriptor instanceof InlineKeyDescriptor);

    if (myBTree == null) {
      try {
        lockStorage();
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
        unlockStorage();
      }
    }
  }

  @NotNull
  private static Path indexFile(@NotNull Path file) {
    return file.resolveSibling(file.getFileName() + "_i");
  }

  private void initBtree(boolean initial) throws IOException {
    myBTree = new IntToIntBtree(BTREE_PAGE_SIZE, indexFile(myFile), myStorage.getPagedFileStorage().getStorageLockContext(), initial);
  }

  private void storeVars(boolean toDisk) {
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

  private void storeBTreeVars(boolean toDisk) {
    final IntToIntBtree tree = myBTree;
    if (tree != null) {
      final int BTREE_DATA_START = DATA_START + 36;
      tree.persistVars(new IntToIntBtree.BtreeDataStorage() {
        @Override
        public int persistInt(int offset, int value, boolean toDisk) {
          return store(BTREE_DATA_START + offset, value, toDisk);
        }
      }, toDisk);
    }
  }

  private int store(int offset, int value, boolean toDisk) {
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
        public boolean process(final int record) {
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
    try {
      lockStorage();
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
    catch (IllegalStateException e) {
      CorruptedException corruptedException = new CorruptedException(myFile);
      corruptedException.initCause(e);
      throw corruptedException;
    }
    finally {
      unlockStorage();
    }
  }

  protected int addrToIndex(int addr) {
    assert myExternalKeysNoMapping;
    return addr + KEY_SHIFT;
  }

  @Override
  protected int indexToAddr(int idx) {
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
  protected int setupValueId(int hashCode, int dataOff) {
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
    assert myInlineKeysNoMapping;
    try {
      lockStorage();
      final boolean hasMapping = myBTree.get(((InlineKeyDescriptor<Data>)myDataDescriptor).toInt(key), myResultBuf);
      if (!hasMapping) {
        return NULL_ID;
      }

      return keyIdToNonNegativeOffset(myResultBuf[0]);
    }
    catch (IllegalStateException e) {
      CorruptedException exception = new CorruptedException(myFile);
      exception.initCause(e);
      throw exception;
    }
    finally {
      unlockStorage();
    }
  }

  long keyIdToNonNegativeOffset(int value) {
    if (value >= 0) return value;
    return myStorage.getLong(-value);
  }

  void putNonNegativeValue(Data key, long value) throws IOException {
    assert value >= 0;
    assert myInlineKeysNoMapping;
    try {
      lockStorage();

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
    catch (IllegalStateException e) {
      CorruptedException exception = new CorruptedException(myFile);
      exception.initCause(e);
      throw exception;
    }
    finally {
      unlockStorage();
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
  protected synchronized int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    try {
      lockStorage();
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

      int newValueId = writeData(value, valueHC);
      ++myValuesCount;

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
    catch (IllegalStateException e) {
      CorruptedException exception = new CorruptedException(myFile);
      exception.initCause(e);
      throw exception;
    }
    finally {
      unlockStorage();
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
    private byte[] myBuffer;

    @Override
    int recordWriteOffset(@NotNull PersistentBTreeEnumerator<?> enumerator, byte @NotNull [] buf) {
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
        myBuffer = enumerator.myInlineKeysNoMapping ? ArrayUtilRt.EMPTY_BYTE_ARRAY : new byte[RECORD_SIZE];
      }
      return myBuffer;
    }

    @Override
    void setupRecord(@NotNull PersistentBTreeEnumerator<?> enumerator, int hashCode, int dataOffset, byte[] buf) {
      if (!enumerator.myInlineKeysNoMapping) Bits.putInt(buf, 0, dataOffset);
    }
  }
}
