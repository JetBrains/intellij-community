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

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PersistentBTreeEnumerator<Data> extends PersistentEnumeratorBase<Data> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentEnumerator");
  protected static final int NULL_ID = 0;
  private static final int PAGE_SIZE = IntToIntBtree.doSanityCheck ? 64:2048;
  private static final int RECORD_SIZE = 4;
  private final byte[] myBuffer;

  private int myLogicalFileLength;
  private int myRootNodeStart;
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

  public PersistentBTreeEnumerator(File file, KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    super(file, new MappedFileEnumeratorStorage(file, initialSize, 1024 * 1024, 10f, false), dataDescriptor, initialSize);

    myBuffer = new byte[getRecordSize()];

    if (btree == null) {
      synchronized (ourLock) {
        storeVars(false);
        initBtree(false);
        storeBTreeVars(false);
      }
    }

    myInlineKeysNoMapping = myDataDescriptor instanceof InlineKeyDescriptor && !wantInlineKeyMapping();
  }

  protected boolean wantInlineKeyMapping() {
    return true;
  }

  private void initBtree(boolean initial) {
    btree = new IntToIntBtree(PAGE_SIZE, myRootNodeStart, initial) {

      @Override
      protected int allocEmptyPage() {
        return PersistentBTreeEnumerator.this.allocPage();
      }

      @Override
      protected void doSavePage(int address, byte[] pageBuffer) {
        if (doSanityCheck) myAssert(pageBuffer.length == PAGE_SIZE);
        myStorage.put(address, pageBuffer, 0, pageBuffer.length);
      }

      @Override
      protected void doLoadPage(int address, byte[] pageBuffer) {
        if (doSanityCheck) myAssert(pageBuffer.length == PAGE_SIZE);
        myStorage.get(address, pageBuffer, 0, pageBuffer.length);
      }

      @Override
      void setRootAddress(int newRootAddress) {
        super.setRootAddress(newRootAddress);
        myRootNodeStart = newRootAddress;
      }
    };
  }

  private void storeVars(boolean toDisk) {
    myLogicalFileLength = store(DATA_START, myLogicalFileLength, toDisk);
    myRootNodeStart = store(DATA_START + 4, myRootNodeStart, toDisk);
    myDataPageStart = store(DATA_START + 8, myDataPageStart, toDisk);
    myDataPageOffset = store(DATA_START + 12, myDataPageOffset, toDisk);
    myFirstPageStart = store(DATA_START + 16, myFirstPageStart, toDisk);
    myDuplicatedValuesPageStart = store(DATA_START + 20, myDuplicatedValuesPageStart, toDisk);
    myDuplicatedValuesPageOffset = store(DATA_START + 24, myDuplicatedValuesPageOffset, toDisk);
    valuesCount = store(DATA_START + 28, valuesCount, toDisk);
    collisions = store(DATA_START + 32, collisions, toDisk);
    storeBTreeVars(toDisk);
  }

  private void storeBTreeVars(boolean toDisk) {
    if (btree != null) {
      btree.setMaxStepsSearched(store(DATA_START + 36, btree.getMaxStepsSearched(), toDisk));
      btree.setPagesCount(store(DATA_START + 40, btree.getPageCount(), toDisk));
    }
  }

  private int store(int offset, int value, boolean toDisk) {
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
    myRootNodeStart = (DATA_START / PAGE_SIZE + 1) * PAGE_SIZE;

    allocEmptyPage(myRootNodeStart);
    myFirstPageStart = myDataPageStart = -1;
    myDuplicatedValuesPageStart = -1;

    initBtree(true);
    storeVars(true);
  }

  @Override
  protected void doClose() throws IOException {
    storeVars(true);
    super.doClose();
  }

  private int allocEmptyPage(int pageStart) {
    byte[] buff = new byte[PAGE_SIZE];
    myStorage.put(pageStart, buff, 0, PAGE_SIZE);

    myLogicalFileLength += PAGE_SIZE;
    return pageStart;
  }

  private int allocPage() {
    return allocEmptyPage(myLogicalFileLength);
  }

  @Override
  public boolean traverseAllRecords(RecordsProcessor p) throws IOException {
    synchronized (ourLock) {
      if (myInlineKeysNoMapping) {
        List<IntToIntBtree.BtreeIndexNodeView> leafPages = new ArrayList<IntToIntBtree.BtreeIndexNodeView> ();
        collectLeafPages(btree.root, leafPages);

        out:
        for(IntToIntBtree.BtreeIndexNodeView value:leafPages) {
          for(int i = 0; i < value.getChildrenCount(); ++i) {
            if (!p.process(value.keyAt(i))) break out;
          }
        }
        return true;
      }

      int current = myFirstPageStart;
      int currentPage = current;
      int end = myDataPageStart + myDataPageOffset;
      byte[] recordBuffer = getRecordBuffer();
      int last = PAGE_SIZE - 4;

      while(current != end) {
        if (current - currentPage >= last) {
          currentPage = current = myStorage.getInt(currentPage + last);
        }
        if (!p.process(current)) break;
        current += recordBuffer.length;
      }
      return true;
    }
  }

  private void collectLeafPages(IntToIntBtree.BtreeIndexNodeView node, List<IntToIntBtree.BtreeIndexNodeView> leafPages) {
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
    int anInt = myStorage.getInt(idx);
    if (IntToIntBtree.doSanityCheck) {
      IntToIntBtree.myAssert(anInt >= 0 || myDataDescriptor instanceof InlineKeyDescriptor);
    }
    return anInt;
  }

  @Override
  protected int getRecordSize() {
    return myInlineKeysNoMapping ? 0:RECORD_SIZE;
  }

  protected int enumerateImpl(final Data value, final boolean saveNewValue) throws IOException {
    try {
      if (IntToIntBtree.doDump) System.out.println(value);
      final int valueHC = myDataDescriptor.getHashCode(value);

      final Integer keyValue = btree.get(valueHC);
      if (keyValue == null && !saveNewValue) {
        return NULL_ID;
      }

      int indexNodeValueAddress = keyValue != null ? keyValue:0;
      int collisionAddress = NULL_ID;

      if (!myInlineKeysNoMapping) {
        indexNodeValueAddress = keyValue != null ? keyValue:0;
        collisionAddress = NULL_ID;

        if (indexNodeValueAddress > 0) {
          // we found reference to no dupe key
          Data candidate = valueOf(indexNodeValueAddress);
          if (IntToIntBtree.doSanityCheck) IntToIntBtree.myAssert(myDataDescriptor.getHashCode(candidate) == valueHC);

          if (myDataDescriptor.isEqual(value, candidate)) {
            return indexNodeValueAddress;
          }

          collisionAddress = indexNodeValueAddress;
        } else if (indexNodeValueAddress < 0) { // indexNodeValueAddress points to duplicates list
          collisionAddress = -indexNodeValueAddress;

          while (true) {
            final int address = myStorage.getInt(collisionAddress);
            Data candidate = valueOf(address);
            if (myDataDescriptor.isEqual(value, candidate)) {
              return address;
            }
            if (IntToIntBtree.doSanityCheck) IntToIntBtree.myAssert(myDataDescriptor.getHashCode(candidate) == valueHC);

            int newCollisionAddress = myStorage.getInt(collisionAddress + COLLISION_OFFSET);
            if (newCollisionAddress == 0) break;
            collisionAddress = newCollisionAddress;
          }
        }

        if (!saveNewValue) return NULL_ID;
      } else {
        if (keyValue != null) return indexNodeValueAddress;
      }

      int newValueId = writeData(value, valueHC);  // TODO: we can store at Btree leaf index
      ++valuesCount;

      if (valuesCount % 10000 == 0 && IOStatistics.DEBUG) {
        IOStatistics.dump("Index " +
                          myFile +
                          ", values " +
                          valuesCount +
                          ", storage size:" +
                          myStorage.length() +
                          ", pagecount:" +
                          btree.getPageCount() +
                          ", height:" +
                          btree.getMaxStepsSearched());
      }

      if (collisionAddress != NULL_ID) {
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
    catch (IOException io) {
      markCorrupted();
      throw io;
    }
    catch (Throwable e) {
      markCorrupted();
      LOG.error(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public Data valueOf(int idx) throws IOException {
    if (myInlineKeysNoMapping) {
      assert false:"No valueOf for inline keys with no mapping option";
    }
    return super.valueOf(idx);
  }

  @Override
  protected int recordWriteOffset(byte[] buf) {
    if (myFirstPageStart == -1) {
      myFirstPageStart = myDataPageStart = allocPage();
    }
    if (myDataPageOffset + buf.length + 4 > btree.pageSize) {
      assert myDataPageOffset + 4 <= btree.pageSize;
      int prevDataPageStart = myDataPageStart + btree.pageSize - 4;
      myDataPageStart = allocPage();
      myStorage.putInt(prevDataPageStart, myDataPageStart);
      myDataPageOffset = 0;
    }

    int recordWriteOffset = myDataPageOffset;
    assert recordWriteOffset + buf.length + 4 <= btree.pageSize;
    myDataPageOffset += buf.length;
    return recordWriteOffset + myDataPageStart;
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


  protected byte[] getRecordBuffer() {
    return myBuffer;
  }

  protected void setupRecord(int hashCode, final int dataOffset, final byte[] buf) {
    Bits.putInt(buf, 0, dataOffset);
  }

  @Override
  protected void doFlush() throws IOException {
    btree.doFlush();
    super.doFlush();
  }
}