/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.PagePool;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.RecordIdIterator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;

// Twice as compact as AbstractRecordsTable: 8 bytes per record: int offset ([0..Integer.MAX_INT]), int ((capacity [0..0xFFFF) << 16) | (size [-1..0xFFFF)))
// if int offset is overflowed then new 8 byte record is created to
// hold long offset and original record contains negative new record number,
// same if size / capacity is overflowed: new record is created to hold integer offset / capacity and original record contains its
// negative number
public class CompactRecordsTable extends AbstractRecordsTable {
  private final byte[] zeroes;
  private final boolean forceSplit;

  public CompactRecordsTable(File recordsFile, PagePool pool, boolean forceSplit) throws IOException {
    super(recordsFile, pool);
    zeroes = new byte[getRecordSize()];
    this.forceSplit = forceSplit;
  }

  @Override
  protected int getImplVersion() {
    return 1;
  }

  @Override
  protected int getRecordSize() {
    return 8;
  }

  @Override
  protected byte[] getZeros() {
    return zeroes;
  }

  private static final int ADDRESS_OFFSET = 0;
  private static final int SIZE_AND_CAPACITY_OFFSET = 4;
  private static final int SIZE_OFFSET_IN_INDIRECT_RECORD = 0;
  private static final int CAPACITY_OFFSET_IN_INDIRECT_RECORD = 4;

  @Override
  public long getAddress(int record) {
    int address = myStorage.getInt(getOffset(record, ADDRESS_OFFSET));
    if (address < 0) { // read address from indirect record
      return super.getAddress(-address);
    }
    return address;
  }

  @Override
  public void setAddress(int record, long address) {
    markDirty();

    int addressOfRecordAbsoluteOffset = getOffset(record, ADDRESS_OFFSET);
    int existing_address = myStorage.getInt(addressOfRecordAbsoluteOffset);

    if (existing_address < 0) { // update address in indirect record
      super.setAddress(-existing_address, address);
      return;
    }
    if (address > Integer.MAX_VALUE || address < 0 || forceSplit) {
      // nonnegative integer address is not enough and we introduce indirect record
      int extendedRecord = doCreateNewRecord();
      super.setAddress(extendedRecord, address);
      myStorage.putInt(addressOfRecordAbsoluteOffset, -extendedRecord);
    }
    else {
      myStorage.putInt(addressOfRecordAbsoluteOffset, (int)address);
    }
  }

  private int doCreateNewRecord() {
    try {
      return createNewRecord();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static final int SIZE_MASK = 0xFFFF;
  private static final int CAPACITY_MASK = 0x7FFF0000;
  private static final int CAPACITY_SHIFT = 16;
  private static final int SPECIAL_POSITIVE_VALUE_FOR_SPECIAL_NEGATIVE_SIZE = 0xFFFF;
  private static final int SPECIAL_NEGATIVE_SIZE = -1;

  @Override
  public int getSize(int record) {
    int currentValue = myStorage.getInt(getOffset(record, SIZE_AND_CAPACITY_OFFSET));
    if (currentValue < 0) {
      // read size from indirect record
      return myStorage.getInt(getOffset(-currentValue, SIZE_OFFSET_IN_INDIRECT_RECORD));
    }
    int i = currentValue & SIZE_MASK;
    if (i == SPECIAL_POSITIVE_VALUE_FOR_SPECIAL_NEGATIVE_SIZE) i = SPECIAL_NEGATIVE_SIZE;
    return i;
  }

  @Override
  public void setSize(int record, int size) {
    markDirty();

    int sizeAndCapacityOfRecordAbsoluteOffset = getOffset(record, SIZE_AND_CAPACITY_OFFSET);
    int currentValue = myStorage.getInt(sizeAndCapacityOfRecordAbsoluteOffset);
    if (currentValue < 0) {
      // update size in indirect record
      myStorage.putInt(getOffset(-currentValue, SIZE_OFFSET_IN_INDIRECT_RECORD), size);
      return;
    }

    // size to fit in normal record [-1 .. 0xFFFF)
    if (size >= 0xFFFF || size < SPECIAL_NEGATIVE_SIZE || forceSplit) {
      // introduce indirect record able to hold larger size range
      extendSizeAndCapacityRecord(record, size, getCapacity(record));
      return;
    }

    if (size == SPECIAL_NEGATIVE_SIZE) {
      size = SPECIAL_POSITIVE_VALUE_FOR_SPECIAL_NEGATIVE_SIZE;
    }

    myStorage.putInt(sizeAndCapacityOfRecordAbsoluteOffset, size | (currentValue & CAPACITY_MASK));
  }

  private void extendSizeAndCapacityRecord(int record, int size, int capacity) {
    int extendedRecord = doCreateNewRecord();
    myStorage.putInt(getOffset(extendedRecord, SIZE_OFFSET_IN_INDIRECT_RECORD), size);
    myStorage.putInt(getOffset(extendedRecord, CAPACITY_OFFSET_IN_INDIRECT_RECORD), capacity);
    myStorage.putInt(getOffset(record, SIZE_AND_CAPACITY_OFFSET), -extendedRecord);
  }

  @Override
  public int getCapacity(int record) {
    int currentValue = myStorage.getInt(getOffset(record, SIZE_AND_CAPACITY_OFFSET));
    if (currentValue < 0) {
      // read capacity from indirect record
      return myStorage.getInt(getOffset(-currentValue, CAPACITY_OFFSET_IN_INDIRECT_RECORD));
    }
    return (currentValue & CAPACITY_MASK) >> CAPACITY_SHIFT;
  }

  @Override
  public void setCapacity(int record, int capacity) {
    markDirty();

    int sizeAndCapacityOfRecordAbsoluteOffset = getOffset(record, SIZE_AND_CAPACITY_OFFSET);
    int currentValue = myStorage.getInt(sizeAndCapacityOfRecordAbsoluteOffset);
    if (currentValue < 0) {
      // update capacity in indirect record
      myStorage.putInt(getOffset(-currentValue, CAPACITY_OFFSET_IN_INDIRECT_RECORD), capacity);
      return;
    }

    // size to fit in normal record [0 .. 0x7FFF]
    if (capacity > 0x7fff || capacity < 0 || forceSplit) {
      // introduce indirect record able to hold larger capacity range
      extendSizeAndCapacityRecord(record, getSize(record), capacity);
      return;
    }
    myStorage.putInt(sizeAndCapacityOfRecordAbsoluteOffset, (currentValue & SIZE_MASK) | (capacity << CAPACITY_SHIFT));
  }

  @Override
  public void deleteRecord(int record) throws IOException {
    final int sizeAndCapacityOfRecordAbsoluteOffset = getOffset(record, SIZE_AND_CAPACITY_OFFSET);
    final int sizeAndCapacityValue = myStorage.getInt(sizeAndCapacityOfRecordAbsoluteOffset);

    final int addressOfRecordAbsoluteOffset = getOffset(record, ADDRESS_OFFSET);
    final int existingAddressValue = myStorage.getInt(addressOfRecordAbsoluteOffset);

    super.deleteRecord(record);

    if (sizeAndCapacityValue < 0) {
      super.deleteRecord(-sizeAndCapacityValue);
    }

    if (existingAddressValue < 0) {
      super.deleteRecord(-existingAddressValue);
    }
  }

  @Override
  public RecordIdIterator createRecordIdIterator() throws IOException {
    final BitSet extraRecordsIds = buildIdSetOfExtraRecords();
    final RecordIdIterator iterator = super.createRecordIdIterator();

    return new RecordIdIterator() {
      int nextId = scanToNextId();

      private int scanToNextId() {
        while(iterator.hasNextId()) {
          int next = iterator.nextId();
          if ( !extraRecordsIds.get(next)) return next;
        }
        return -1;
      }

      @Override
      public boolean hasNextId() {
        return nextId != -1;
      }

      @Override
      public int nextId() {
        assert hasNextId();
        int result = nextId;
        nextId = scanToNextId();
        return result;
      }

      @Override
      public boolean validId() {
        assert hasNextId();
        return getSize(nextId) != -1;
      }
    };
  }

  @NotNull
  private BitSet buildIdSetOfExtraRecords() throws IOException {
    final BitSet extraRecords = new BitSet();

    final RecordIdIterator iterator = super.createRecordIdIterator();
    while(iterator.hasNextId()) {
      int recordId = iterator.nextId();
      final int sizeAndCapacityOfRecordAbsoluteOffset = getOffset(recordId, SIZE_AND_CAPACITY_OFFSET);
      final int sizeAndCapacityValue = myStorage.getInt(sizeAndCapacityOfRecordAbsoluteOffset);

      final int addressOfRecordAbsoluteOffset = getOffset(recordId, ADDRESS_OFFSET);
      final int existingAddressValue = myStorage.getInt(addressOfRecordAbsoluteOffset);

      if (sizeAndCapacityValue < 0) {
        extraRecords.set(-sizeAndCapacityValue);
      }

      if (existingAddressValue < 0) {
        extraRecords.set(-existingAddressValue);
      }
    }
    return extraRecords;
  }
}
