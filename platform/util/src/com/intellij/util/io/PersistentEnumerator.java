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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 * @author jeka
 */
public class PersistentEnumerator<Data> extends PersistentEnumeratorBase<Data> {
  private static final int DIRTY_MAGIC = 0xbabe0589;
  private static final int VERSION = 6;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafac + VERSION;

  private static final int FIRST_VECTOR_OFFSET = DATA_START;

  private static final int BITS_PER_LEVEL = 4;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;
  private static final byte[] EMPTY_VECTOR = new byte[SLOTS_PER_VECTOR * 4];

  private static final int BITS_PER_FIRST_LEVEL = 12;
  private static final int SLOTS_PER_FIRST_VECTOR = 1 << BITS_PER_FIRST_LEVEL;
  private static final int FIRST_LEVEL_MASK = SLOTS_PER_FIRST_VECTOR - 1;
  private static final byte[] FIRST_VECTOR = new byte[SLOTS_PER_FIRST_VECTOR * 4];

  private static final int COLLISION_OFFSET = 0;
  private static final int KEY_HASHCODE_OFFSET = COLLISION_OFFSET + 4;
  private static final int KEY_REF_OFFSET = KEY_HASHCODE_OFFSET + 4;
  private static final int RECORD_SIZE = KEY_REF_OFFSET + 4;
  private int valuesCount; // TODO: valuesCount should be persistent
  private static final Version ourVersion = new Version(CORRECTLY_CLOSED_MAGIC, DIRTY_MAGIC);

  public PersistentEnumerator(@NotNull File file, @NotNull KeyDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentEnumerator(@NotNull File file,
                              @NotNull KeyDescriptor<Data> dataDescriptor,
                              int initialSize,
                              @Nullable PagedFileStorage.StorageLockContext storageLockContext) throws IOException {
    super(file, new ResizeableMappedFile(file, initialSize, storageLockContext, -1, false), dataDescriptor, initialSize, ourVersion,
          new RecordBufferHandler(), true);
  }

  @Override
  protected  void setupEmptyFile() throws IOException {
    allocVector(FIRST_VECTOR);
  }

  @Override
  public synchronized boolean traverseAllRecords(@NotNull RecordsProcessor p) throws IOException {
    return traverseRecords(FIRST_VECTOR_OFFSET, SLOTS_PER_FIRST_VECTOR, p);
  }

  private boolean traverseRecords(int vectorStart, int slotsCount, @NotNull RecordsProcessor p) throws IOException {
    lockStorage();
    try {
      for (int slotIdx = 0; slotIdx < slotsCount; slotIdx++) {
        final int vector = myStorage.getInt(vectorStart + slotIdx * 4);
        if (vector < 0) {
          for (int record = -vector; record != 0; record = nextCanditate(record)) {
            if (!p.process(record)) return false;
          }
        }
        else if (vector > 0) {
          if (!traverseRecords(vector, SLOTS_PER_VECTOR, p)) return false;
        }
      }
      return true;
    }
    finally {
      unlockStorage();
    }
  }

  @Override
  protected synchronized int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    lockStorage();
    try {
      int depth = 0;
      final int valueHC = myDataDescriptor.getHashCode(value);
      int hc = valueHC;
      int vector = FIRST_VECTOR_OFFSET;
      int pos;
      int lastVector;

      int levelMask = FIRST_LEVEL_MASK;
      int bitsPerLevel = BITS_PER_FIRST_LEVEL;
      do {
        lastVector = vector;
        pos = vector + (hc & levelMask) * 4;
        hc >>>= bitsPerLevel;
        vector = myStorage.getInt(pos);
        depth++;

        levelMask = LEVEL_MASK;
        bitsPerLevel = BITS_PER_LEVEL;
      }
      while (vector > 0);

      if (vector == 0) {
        // Empty slot
        if (onlyCheckForExisting) {
          return NULL_ID;
        }
        final int newId = writeData(value, valueHC);
        myStorage.putInt(pos, -newId);
        return newId;
      }
      else {
        int collision = -vector;
        boolean splitVector = false;
        int candidateHC;
        do {
          candidateHC = hashCodeOf(collision);
          if (candidateHC != valueHC) {
            splitVector = true;
            break;
          }

          Data candidate = valueOf(collision);
          if (myDataDescriptor.isEqual(value, candidate)) {
            return collision;
          }

          collision = nextCanditate(collision);
        }
        while (collision != 0);

        if (onlyCheckForExisting) {
          return NULL_ID;
        }

        final int newId = writeData(value, valueHC);

        if (splitVector) {
          depth--;
          do {
            final int valueHCByte = hcByte(valueHC, depth);
            final int oldHCByte = hcByte(candidateHC, depth);
            if (valueHCByte == oldHCByte) {
              int newVector = allocVector(EMPTY_VECTOR);
              myStorage.putInt(lastVector + oldHCByte * 4, newVector);
              lastVector = newVector;
            }
            else {
              myStorage.putInt(lastVector + valueHCByte * 4, -newId);
              myStorage.putInt(lastVector + oldHCByte * 4, vector);
              break;
            }
            depth++;
          }
          while (true);
        }
        else {
          // Hashcode collision detected. Insert new string into the list of colliding.
          myStorage.putInt(newId, vector);
          myStorage.putInt(pos, -newId);
        }
        return newId;
      }
    }
    finally {
      unlockStorage();
    }
  }

  @Override
  protected int writeData(final Data value, int hashCode) {
    int id = super.writeData(value, hashCode);
    ++valuesCount;

    if (IOStatistics.DEBUG && (valuesCount & IOStatistics.KEYS_FACTOR_MASK) == 0) {
      IOStatistics.dump("Index " + myFile + ", values " + valuesCount + ", storage size:" + myStorage.length());
    }
    return id;
  }

  private static int hcByte(int hashcode, int byteN) {
    if (byteN == 0) {
      return hashcode & FIRST_LEVEL_MASK;
    }

    hashcode >>>= BITS_PER_FIRST_LEVEL;
    byteN--;

    return (hashcode >>> (byteN * BITS_PER_LEVEL)) & LEVEL_MASK;
  }

  private int allocVector(@NotNull final byte[] empty) throws IOException {
    final int pos = (int)myStorage.length();
    myStorage.put(pos, empty, 0, empty.length);
    return pos;
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  private int hashCodeOf(int idx) throws IOException {
    return myStorage.getInt(idx + KEY_HASHCODE_OFFSET);
  }

  @Override
  protected int indexToAddr(int idx) {
    return myStorage.getInt(idx + KEY_REF_OFFSET);
  }

  private static class RecordBufferHandler extends PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumerator> {
    private final byte[] myBuffer = new byte[RECORD_SIZE];

    @Override
    protected int recordWriteOffset(@NotNull PersistentEnumerator enumerator, byte[] buf) {
      return (int)enumerator.myStorage.length();
    }

    @NotNull
    @Override
    byte[] getRecordBuffer(PersistentEnumerator t) {
      return myBuffer;
    }

    @Override
    void setupRecord(PersistentEnumerator enumerator, int hashCode, int dataOffset, byte[] buf) {
      Bits.putInt(buf, COLLISION_OFFSET, 0);
      Bits.putInt(buf, KEY_HASHCODE_OFFSET, hashCode);
      Bits.putInt(buf, KEY_REF_OFFSET, dataOffset);
    }
  }
}
