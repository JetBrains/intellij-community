// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.intmultimaps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * int->int* hash map specialized for (String) hash->id mapping.
 * Open addressing, with linear probing.
 * Map relies on the fact id=0 is not valid id, hence map prohibits 0 for keys & values, and uses 0 to identify
 * 'empty' and 'deleted' entries.
 */
@ApiStatus.Internal
public final class Int2IntMultimap {
  public static final int NO_VALUE = 0;

  private final float loadFactor;

  /**
   * (key, value) = (table[2*i], table[2*i+1])
   * key = NO_VALUE, value  = NO_VALUE -> empty slot (not yet allocated)
   * key = NO_VALUE, value != NO_VALUE -> 'tombstone', i.e. deleted slot (key-value pair was inserted and removed)
   */
  private int @NotNull [] table;
  private int aliveValues = 0;
  /**
   * alive + deleted (tombstone)
   */
  private int filledSlots = 0;

  public Int2IntMultimap() {
    this(16, 0.4f);
  }

  public Int2IntMultimap(int capacity,
                         float loadFactor) {
    this.loadFactor = loadFactor;
    this.table = new int[capacity * 2];

    Arrays.fill(table, NO_VALUE);
  }

  /**
   * @return true if iterated through all values, false if iteration was stopped early by valuesProcessor returning false
   */
  public boolean lookup(int key,
                        IntPredicate valuesProcessor) {
    checkNotNoValue("key", key);
    int capacity = capacity();
    int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      int slotIndex = (startIndex + probe) % capacity;
      int slotKey = table[slotIndex * 2];
      int slotValue = table[slotIndex * 2 + 1];
      if (slotKey == key) {
        assert slotValue != NO_VALUE : "value(table[" + (slotIndex * 2 + 1) + "]) = " + NO_VALUE + " (NO_VALUE), " +
                                       "while key(table[" + slotIndex * 2 + "]) = " + key;
        if (!valuesProcessor.test(slotValue)) {
          return false;
        }
      }
      else if (slotKey == NO_VALUE && slotValue == NO_VALUE) {
        //free slot -> end of probing sequence, no (key, value) found:
        break;
      }
    }
    return true;
  }

  public boolean has(int key,
                     int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    int capacity = capacity();
    int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      int slotIndex = (startIndex + probe) % capacity;
      int slotKey = table[slotIndex * 2];
      int slotValue = table[slotIndex * 2 + 1];
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


  public boolean put(int key,
                     int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    int capacity = capacity();
    int startIndex = Math.abs(key % capacity);
    int firstTombstoneIndex = -1;
    for (int probe = 0; probe < capacity; probe++) {
      int slotIndex = (startIndex + probe) % capacity;
      int slotKey = table[slotIndex * 2];
      int slotValue = table[slotIndex * 2 + 1];
      if (slotKey == key && slotValue == value) {
        return false;//record already here, nothing to add
      }

      if (slotKey == NO_VALUE) {
        if (slotValue != NO_VALUE) {
          //slot removed -> remember index, but continue lookup
          if (firstTombstoneIndex == -1) {
            firstTombstoneIndex = slotIndex;
          }
        }
        else {
          //(NO_VALUE, NO_VALUE) -> free slot -> end of probing sequence, no (key, value) found -> insert it:
          int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : slotIndex;
          table[insertionIndex * 2] = key;
          table[insertionIndex * 2 + 1] = value;
          aliveValues++;
          break;
        }
      }
    }

    if (aliveValues > capacity * loadFactor) {
      //resize:
      Int2IntMultimap newMMap = new Int2IntMultimap(capacity * 2, loadFactor);
      forEach((_key, _value) -> {
        newMMap.put(_key, _value);
        return true;
      });
      this.table = newMMap.table;
      this.aliveValues = newMMap.aliveValues;
      this.filledSlots = newMMap.aliveValues;
    }
    return true;
  }

  public boolean remove(int key,
                        int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    int capacity = capacity();
    int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      int slotIndex = (startIndex + probe) % capacity;
      int slotKey = table[slotIndex * 2];
      int slotValue = table[slotIndex * 2 + 1];
      if (slotKey == key && slotValue == value) {
        //reset key, but leave value as-is: this is the marker of 'removed' slot
        table[slotIndex * 2] = NO_VALUE;
        aliveValues--;
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

  public void forEach(@NotNull KeyValueProcessor processor) {
    for (int i = 0; i < table.length; i += 2) {
      int key = table[i];
      int value = table[i + 1];
      if (key != NO_VALUE) {
        assert value != NO_VALUE : "value(table[" + (i + 1) + "]) = " + NO_VALUE + ", while key(table[" + i + "]) = " + key;
        if (!processor.process(key, value)) {
          return;
        }
      }
    }
  }

  public int sizeInBytes() {
    //skip Int2IntMultimap fields and array object header:
    return table.length * Integer.BYTES;
  }

  public int capacity() {
    return table.length / 2;
  }

  public int size() {
    return aliveValues;
  }

  public boolean replace(int key,
                         int oldValue,
                         int newValue) {
    checkNotNoValue("key", key);
    checkNotNoValue("oldValue", oldValue);
    checkNotNoValue("newValue", newValue);

    int capacity = capacity();
    int startIndex = Math.abs(key % capacity);
    //BEWARE: .replace() must maintain an invariant that key's values is a _set_ -- not just a list.
    // I.e. if newValue is already exist among the key's values -- oldValue should NOT be replaced, but just removed,
    // to not create 2 newValue entries => we need to look for both old & newValue first, and only then decide
    // how to behave:
    int oldValueSlotIndex = -1;
    int newValueSlotIndex = -1;
    for (int probe = 0; probe < capacity; probe++) {
      int slotIndex = (startIndex + probe) % capacity;
      int slotKey = table[slotIndex * 2];
      int slotValue = table[slotIndex * 2 + 1];
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
        table[oldValueSlotIndex * 2] = NO_VALUE;
        aliveValues--;
      }
      else {
        //newValue is not exists in key's values set
        // => update slot (old->new)Value:
        table[oldValueSlotIndex * 2 + 1] = newValue;
      }
      return true;
    }
    else {
      //oldValue is not exist -> do nothing
      return false;
    }
  }

  @FunctionalInterface
  public interface KeyValueProcessor {
    boolean process(int key,
                    int value);
  }

  private static void checkNotNoValue(String paramName,
                                      int value) {
    if (value == NO_VALUE) {
      throw new IllegalArgumentException(paramName + " can't be = " + NO_VALUE + " -- it is special value used as NO_VALUE");
    }
  }
}
