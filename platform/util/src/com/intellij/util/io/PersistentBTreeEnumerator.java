/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
  private int valuesCount;
  private int collisions;

  private IntToIntBtree btree;
  private final boolean myInlineKeysNoMapping;
  private boolean myExternalKeysNoMapping;

  private static final int DIRTY_MAGIC = 0xbabe1977;
  private static final int VERSION = 6 + IntToIntBtree.VERSION;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafc + VERSION + PAGE_SIZE;
  @NotNull private static Version ourVersion = new Version(CORRECTLY_CLOSED_MAGIC, DIRTY_MAGIC);
  private static final int KEY_SHIFT = 1;

  public PersistentBTreeEnumerator(@NotNull File file, @NotNull KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    super(file, new ResizeableMappedFile(file, initialSize, ourLock, VALUE_PAGE_SIZE, true), dataDescriptor, initialSize,
          ourVersion, new RecordBufferHandler(), false);

    myInlineKeysNoMapping = myDataDescriptor instanceof InlineKeyDescriptor && !wantKeyMapping();
    myExternalKeysNoMapping = !(myDataDescriptor instanceof InlineKeyDescriptor) && !wantKeyMapping();

    if (btree == null) {
      synchronized (ourLock) {
        storeVars(false);
        initBtree(false);
        storeBTreeVars(false);
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
    btree = new IntToIntBtree(PAGE_SIZE, indexFile(myFile), initial);
  }

  private void storeVars(boolean toDisk) {
    myLogicalFileLength = store(DATA_START, myLogicalFileLength, toDisk);
    myDataPageStart = store(DATA_START + 4, myDataPageStart, toDisk);
    myDataPageOffset = store(DATA_START + 8, myDataPageOffset, toDisk);
    myFirstPageStart = store(DATA_START + 12, myFirstPageStart, toDisk);
    myDuplicatedValuesPageStart = store(DATA_START + 16, myDuplicatedValuesPageStart, toDisk);
    myDuplicatedValuesPageOffset = store(DATA_START + 20, myDuplicatedValuesPageOffset, toDisk);
    valuesCount = store(DATA_START + 24, valuesCount, toDisk);
    collisions = store(DATA_START + 28, collisions, toDisk);
    storeBTreeVars(toDisk);
  }

  private void storeBTreeVars(boolean toDisk) {
    if (btree != null) {
      final int BTREE_DATA_START = DATA_START + 32;
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
      myStorage.putInt(offset, value);
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

  public boolean processAllDataObject(@NotNull final Processor<Data> processor, @Nullable final DataFilter filter) throws IOException {
    if(myInlineKeysNoMapping) {
      return traverseAllRecords(new RecordsProcessor() {
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
  public boolean traverseAllRecords(@NotNull RecordsProcessor p) throws IOException {
    try {
      synchronized (ourLock) {

        List<IntToIntBtree.BtreeIndexNodeView> leafPages = new ArrayList<IntToIntBtree.BtreeIndexNodeView> ();
        btree.doFlush();
        btree.root.syncWithStore();
        collectLeafPages(btree.root, leafPages);
        Collections.sort(leafPages, new Comparator<IntToIntBtree.BtreeIndexNodeView>() {
          @Override
          public int compare(@NotNull IntToIntBtree.BtreeIndexNodeView o1, @NotNull IntToIntBtree.BtreeIndexNodeView o2) {
            return o1.address - o2.address;
          }
        });

        out:
        for(IntToIntBtree.BtreeIndexNodeView page:leafPages) {
          for(int key:page.exportKeys()) {
            boolean hasMapping = btree.get(key, myResultBuf);
            p.setCurrentKey(key);
            assert hasMapping;
            int record = myResultBuf[0];

            if (record > 0) {
              if (!p.process(record)) return false;
            } else {
              int rec = - record;
              while(rec != 0) {
                int id = myStorage.getInt(rec);
                if (!p.process(id)) return false;
                rec = myStorage.getInt(rec + COLLISION_OFFSET);
              }
            }
          }
        }
        return true;
      }
    }
    catch (IllegalStateException e) {
      CorruptedException corruptedException = new CorruptedException(myFile);
      corruptedException.initCause(e);
      throw corruptedException;
    }
  }

  private void collectLeafPages(@NotNull IntToIntBtree.BtreeIndexNodeView node, @NotNull List<IntToIntBtree.BtreeIndexNodeView> leafPages) {
    if (node.isIndexLeaf()) {
      leafPages.add(node);
      return;
    }
    for(int i = 0; i <= node.getChildrenCount(); ++i) {
      IntToIntBtree.BtreeIndexNodeView newNode = new IntToIntBtree.BtreeIndexNodeView(btree);
      newNode.setAddress(-node.addressAt(i));
      collectLeafPages(newNode, leafPages);
    }
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
    if (myExternalKeysNoMapping) return dataOff + KEY_SHIFT;
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
  
  protected synchronized int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    try {
      synchronized (ourLock) {
        if (IntToIntBtree.doDump) System.out.println(value);
        final int valueHC = myDataDescriptor.getHashCode(value);

        final boolean hasMapping = btree.get(valueHC, myResultBuf);
        if (!hasMapping && onlyCheckForExisting) {
          return NULL_ID;
        }

        int indexNodeValueAddress = hasMapping ? myResultBuf[0]:0;
        int collisionAddress = NULL_ID;
        Data existingData = null;

        if (!myInlineKeysNoMapping) {
          collisionAddress = NULL_ID;

          if (indexNodeValueAddress > 0) {
            // we found reference to no dupe key
            Data candidate = valueOf(indexNodeValueAddress);
            if (IntToIntBtree.doSanityCheck) IntToIntBtree.myAssert(myDataDescriptor.getHashCode(candidate) == valueHC);

            if (myDataDescriptor.isEqual(value, candidate)) {
              if (!saveNewValue) return indexNodeValueAddress;
              existingData = candidate;
            }

            collisionAddress = indexNodeValueAddress;
          } else if (indexNodeValueAddress < 0) { // indexNodeValueAddress points to duplicates list
            collisionAddress = -indexNodeValueAddress;

            while (true) {
              final int address = myStorage.getInt(collisionAddress);
              Data candidate = valueOf(address);
              if (myDataDescriptor.isEqual(value, candidate)) {
                if (!saveNewValue) return address;
                existingData = candidate;
                break;
              }
              if (IntToIntBtree.doSanityCheck) IntToIntBtree.myAssert(myDataDescriptor.getHashCode(candidate) == valueHC);

              int newCollisionAddress = myStorage.getInt(collisionAddress + COLLISION_OFFSET);
              if (newCollisionAddress == 0) break;
              collisionAddress = newCollisionAddress;
            }
          }

          if (onlyCheckForExisting) return NULL_ID;
        } else {
          if (hasMapping) {
            if(!saveNewValue) return indexNodeValueAddress;
            existingData = value;
          }
        }

        int newValueId = writeData(value, valueHC);
        ++valuesCount;

        if (valuesCount % IOStatistics.KEYS_FACTOR == 0 && IOStatistics.DEBUG) {
          IOStatistics.dump("Index " +
                            myFile +
                            ", values " +
                            valuesCount +
                            ", storage size:" +
                            myStorage.length());
          btree.dumpStatistics();
        }

        if (collisionAddress != NULL_ID) {
          if (existingData != null) {
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
              ++collisions;
            }

            ++collisions;
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
    }
    catch (IllegalStateException e) {
      CorruptedException exception = new CorruptedException(myFile);
      exception.initCause(e);
      throw exception;
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
