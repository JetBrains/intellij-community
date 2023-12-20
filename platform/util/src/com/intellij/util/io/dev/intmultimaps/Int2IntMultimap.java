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

  public Int2IntMultimap(final int capacity,
                         final float loadFactor) {
    this.loadFactor = loadFactor;
    this.table = new int[capacity * 2];

    Arrays.fill(table, NO_VALUE);
  }

  /**
   * @return true if iterated through all values, false if iteration was stopped early by valuesProcessor returning false
   */
  public boolean lookup(final int key,
                        final IntPredicate valuesProcessor) {
    checkNotNoValue("key", key);
    final int capacity = capacity();
    final int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      final int slotIndex = (startIndex + probe) % capacity;
      final int slotKey = table[slotIndex * 2];
      final int slotValue = table[slotIndex * 2 + 1];
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

  public boolean has(final int key,
                     final int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    final int capacity = capacity();
    final int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      final int slotIndex = (startIndex + probe) % capacity;
      final int slotKey = table[slotIndex * 2];
      final int slotValue = table[slotIndex * 2 + 1];
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


  public boolean put(final int key,
                     final int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    final int capacity = capacity();
    final int startIndex = Math.abs(key % capacity);
    int firstTombstoneIndex = -1;
    for (int probe = 0; probe < capacity; probe++) {
      final int slotIndex = (startIndex + probe) % capacity;
      final int slotKey = table[slotIndex * 2];
      final int slotValue = table[slotIndex * 2 + 1];
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
          final int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : slotIndex;
          table[insertionIndex * 2] = key;
          table[insertionIndex * 2 + 1] = value;
          aliveValues++;
          break;
        }
      }
    }

    if (aliveValues > capacity * loadFactor) {
      //resize:
      final Int2IntMultimap newMMap = new Int2IntMultimap(capacity * 2, loadFactor);
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

  public boolean remove(final int key,
                        final int value) {
    checkNotNoValue("key", key);
    checkNotNoValue("value", value);
    final int capacity = capacity();
    final int startIndex = Math.abs(key % capacity);
    for (int probe = 0; probe < capacity; probe++) {
      final int slotIndex = (startIndex + probe) % capacity;
      final int slotKey = table[slotIndex * 2];
      final int slotValue = table[slotIndex * 2 + 1];
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

  public void forEach(final KeyValueProcessor processor) {
    for (int i = 0; i < table.length; i += 2) {
      final int key = table[i];
      final int value = table[i + 1];
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
