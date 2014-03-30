/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class PersistentBTreeEnumerator<Data> extends PersistentEnumeratorBase<Data> {
  private static final int PAGE_SIZE;
  private static final int DEFAULT_PAGE_SIZE = 4096;

  static {
    int pageSize;
    try {
      String property = System.getProperty("idea.btree.page.size");
      pageSize = property != null ? Integer.parseInt(property, 10):DEFAULT_PAGE_SIZE;
    } catch (NumberFormatException ex) {
      pageSize = DEFAULT_PAGE_SIZE;
    }
    PAGE_SIZE = pageSize;
  }

  private static final int RECORD_SIZE = 4;
  private static final int VALUE_PAGE_SIZE = 1024 * 1024;
  static {
    assert VALUE_PAGE_SIZE % PAGE_SIZE == 0:"Page size should be divisor of " + VALUE_PAGE_SIZE;
  }

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

  private IntToIntBtree btree;
  private final boolean myInlineKeysNoMapping;
  private boolean myExternalKeysNoMapping;

  private static final int DIRTY_MAGIC = 0xbabe1977;
  private static final int VERSION = 7 + IntToIntBtree.version();
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafd + VERSION + PAGE_SIZE;
  @NotNull private static final Version ourVersion = new Version(CORRECTLY_CLOSED_MAGIC, DIRTY_MAGIC);
  private static final int KEY_SHIFT = 1;

  public PersistentBTreeEnumerator(@NotNull File file, @NotNull KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentBTreeEnumerator(@NotNull File file,
                                   @NotNull KeyDescriptor<Data> dataDescriptor,
                                   int initialSize,
                                   @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
    super(file,
          new ResizeableMappedFile(
            file,
            initialSize,
            lockContext,
            VALUE_PAGE_SIZE,
            true,
            IOUtil.ourByteBuffersUseNativeByteOrder
          ),
          dataDescriptor,
          initialSize,
          ourVersion,
          new RecordBufferHandler(),
          false
    );

    myInlineKeysNoMapping = myDataDescriptor instanceof InlineKeyDescriptor && !wantKeyMapping();
    myExternalKeysNoMapping = !(myDataDescriptor instanceof InlineKeyDescriptor) && !wantKeyMapping();

    if (btree == null) {
      try {
        lockStorage();
        storeVars(false);
        initBtree(false);
        storeBTreeVars(false);
      }
      finally {
        unlockStorage();
      }
    }
  }

  @NotNull
  private File indexFile(@NotNull File file) {
    return new File(file.getPath() + "_i");
  }

  protected boolean wantKeyMapping() {
    return false;
  }

  private void initBtree(boolean initial) throws IOException {
    btree = new IntToIntBtree(PAGE_SIZE, indexFile(myFile), myStorage.getPagedFileStorage().getStorageLockContext(), initial);
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
    if (btree != null) {
      final int BTREE_DATA_START = DATA_START + 36;
      btree.persistVars(new IntToIntBtree.BtreeDataStorage() {
        @Override
        public int persistInt(int offset, int value, boolean toDisk) {
          return store(BTREE_DATA_START + offset, value, toDisk);
        }
      }, toDisk);
    }
  }

  private int store(int offset, int value, boolean toDisk) {
    assert offset + 4 < PAGE_SIZE;

    if (toDisk) {
      if (myFirstPageStart == -1 || myStorage.getInt(offset) != value) myStorage.putInt(offset, value);
    } else {
      value = myStorage.getInt(offset);
    }
    return value;
  }

  @Override
  protected void setupEmptyFile() throws IOException {
    myLogicalFileLength = PAGE_SIZE;
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
      btree.doClose();
    }
  }

  private int allocPage() {
    int pageStart = myLogicalFileLength;
    myLogicalFileLength += PAGE_SIZE;
    return pageStart;
  }

  @Override
  public boolean processAllDataObject(@NotNull final Processor<Data> processor, @Nullable final DataFilter filter) throws IOException {
    if(myInlineKeysNoMapping) {
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
    try {
      lockStorage();
      return btree.processMappings(new IntToIntBtree.KeyValueProcessor() {
        @Override
        public boolean process(int key, int value) throws IOException {
          p.setCurrentKey(key);

          if (value > 0) {
            if (!p.process(value)) return false;
          }
          else {
            int rec = -value;
            while (rec != 0) {
              int id = myStorage.getInt(rec);
              if (!p.process(id)) return false;
              rec = myStorage.getInt(rec + COLLISION_OFFSET);
            }
          }
          return true;
        }
      });
    }
    catch (IllegalStateException e) {
      CorruptedException corruptedException = new CorruptedException(myFile);
      corruptedException.initCause(e);
      throw corruptedException;
    } finally {
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
    final PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase> recordHandler = getRecordHandler();
    final byte[] buf = recordHandler.getRecordBuffer(this);

    // optimization for using putInt / getInt on aligned empty storage (our page always contains on storage ByteBuffer)
    final int pos = recordHandler.recordWriteOffset(this, buf);
    myStorage.ensureSize(pos + buf.length);

    if (!myInlineKeysNoMapping) myStorage.putInt(pos, dataOff);
    return pos;
  }

  @Override
  public void setRecordHandler(@NotNull PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase> recordHandler) {
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

  @Override
  protected int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    try {
      lockStorage();
      if (IntToIntBtree.doDump) System.out.println(value);
      final int valueHC = myDataDescriptor.getHashCode(value);

      final boolean hasMapping = btree.get(valueHC, myResultBuf);
      if (!hasMapping && onlyCheckForExisting) {
        return NULL_ID;
      }

      final int indexNodeValueAddress = hasMapping ? myResultBuf[0]:0;
      int collisionAddress = NULL_ID;
      boolean hasExistingData = false;

      if (!myInlineKeysNoMapping) {
        collisionAddress = NULL_ID;

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
        } else if (indexNodeValueAddress < 0) { // indexNodeValueAddress points to duplicates list
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
      } else {
        if (hasMapping) {
          if(!saveNewValue) return indexNodeValueAddress;
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
                          ", existing keys enumerated:"+ myExistingKeysEnumerated +
                          ", storage size:" +
                          myStorage.length());
        btree.dumpStatistics();
      }

      if (collisionAddress != NULL_ID) {
        if (hasExistingData) {
          if (indexNodeValueAddress > 0) {
            btree.put(valueHC, newValueId);
          } else {
            myStorage.putInt(collisionAddress, newValueId);
          }
        } else {
          if (indexNodeValueAddress > 0) {
            // organize collision type reference
            int duplicatedValueOff = nextDuplicatedValueRecord();
            btree.put(valueHC, -duplicatedValueOff);

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
      } else {
        btree.put(valueHC, newValueId);
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
    } finally {
      unlockStorage();
    }
  }

  @Override
  boolean canReEnumerate() {
    return true;
  }

  @Override
  public Data valueOf(int idx) throws IOException {
    if (myInlineKeysNoMapping) {
      assert false:"No valueOf for inline keys with no mapping option";
    }
    return super.valueOf(idx);
  }

  private int nextDuplicatedValueRecord() {
    if (myDuplicatedValuesPageStart == -1 || myDuplicatedValuesPageOffset == btree.pageSize) {
      myDuplicatedValuesPageStart = allocPage();
      myDuplicatedValuesPageOffset = 0;
    }

    int duplicatedValueOff = myDuplicatedValuesPageOffset;
    myDuplicatedValuesPageOffset += COLLISION_OFFSET + 4;
    return myDuplicatedValuesPageStart + duplicatedValueOff;
  }

  @Override
  protected void doFlush() throws IOException {
    btree.doFlush();
    storeVars(true);
    super.doFlush();
  }

  private static class RecordBufferHandler extends PersistentEnumeratorBase.RecordBufferHandler<PersistentBTreeEnumerator> {
    private byte[] myBuffer;

    @Override
    int recordWriteOffset(@NotNull PersistentBTreeEnumerator enumerator, @NotNull byte[] buf) {
      if (enumerator.myFirstPageStart == -1) {
        enumerator.myFirstPageStart = enumerator.myDataPageStart = enumerator.allocPage();
      }
      if (enumerator.myDataPageOffset + buf.length + 4 > enumerator.btree.pageSize) {
        assert enumerator.myDataPageOffset + 4 <= enumerator.btree.pageSize;
        int prevDataPageStart = enumerator.myDataPageStart + enumerator.btree.pageSize - 4;
        enumerator.myDataPageStart = enumerator.allocPage();
        enumerator.myStorage.putInt(prevDataPageStart, enumerator.myDataPageStart);
        enumerator.myDataPageOffset = 0;
      }

      int recordWriteOffset = enumerator.myDataPageOffset;
      assert recordWriteOffset + buf.length + 4 <= enumerator.btree.pageSize;
      enumerator.myDataPageOffset += buf.length;
      return recordWriteOffset + enumerator.myDataPageStart;
    }

    @NotNull
    @Override
    byte[] getRecordBuffer(@NotNull PersistentBTreeEnumerator enumerator) {
      if (myBuffer == null) {
        myBuffer = new byte[enumerator.myInlineKeysNoMapping ? 0:RECORD_SIZE];
      }
      return myBuffer;
    }

    @Override
    void setupRecord(@NotNull PersistentBTreeEnumerator enumerator, int hashCode, int dataOffset, byte[] buf) {
      if (!enumerator.myInlineKeysNoMapping) Bits.putInt(buf, 0, dataOffset);
    }
  }
}
