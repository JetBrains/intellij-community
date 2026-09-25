// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;

/// Keeps an `int -> long*` mapping in memory.
/// The owner must serialize operations because this implementation is a first non-persistent prototype.
@ApiStatus.Internal
public final class InMemoryIntToMultiLongMap implements IntToMultiLongMap {

  //TODO RC: quite ineffective way of mapping
  private final Int2ObjectMap<LongArrayList> valuesByKey = new Int2ObjectOpenHashMap<>();
  private int size;

  @Override
  public boolean put(int key, long value) {
    requireValue(value);
    var values = valuesByKey.computeIfAbsent(key, _ -> new LongArrayList());
    if (values.contains(value)) {
      return false;
    }
    values.add(value);
    size++;
    return true;
  }

  @Override
  public boolean replace(int key, long oldValue, long newValue) {
    requireValue(oldValue);
    requireValue(newValue);
    var values = valuesByKey.get(key);
    if (values == null) {
      return false;
    }
    var index = values.indexOf(oldValue);
    if (index < 0) {
      return false;
    }
    if (oldValue != newValue && values.contains(newValue)) {
      values.removeLong(index);
      size--;
    }
    else {
      values.set(index, newValue);
    }
    return true;
  }

  @Override
  public long lookup(int key, @NotNull ValueAcceptor valueAcceptor) throws IOException {
    var values = valuesByKey.get(key);
    if (values == null) {
      return NO_VALUE;
    }
    for (var index = 0; index < values.size(); index++) {
      var value = values.getLong(index);
      if (valueAcceptor.accept(value)) {
        return value;
      }
    }
    return NO_VALUE;
  }

  /// This implementation discards all requested changes if the processor throws an exception.
  @Override
  public boolean lookupAndModify(int key, @NotNull ValueProcessor processor) throws IOException {
    record Modification(int index, long newValue) { }
    var values = valuesByKey.get(key);
    var modifications = new ArrayList<Modification>();
    var newValueRef = new MutableLongRef(NO_VALUE);
    boolean processedAllStoredValues = true;
    if (values != null) {
      for (var index = 0; index < values.size(); index++) {
        long oldValue = values.getLong(index);
        newValueRef.set(oldValue);
        boolean shouldContinue = processor.process(oldValue, newValueRef);
        long newValue = newValueRef.get();
        if (newValue != oldValue) {
          modifications.add(new Modification(index, newValue));
        }
        if (!shouldContinue) {
          processedAllStoredValues = false;
          break;
        }
      }
    }

    boolean processedAll = false;
    long valueToInsert = NO_VALUE;
    if (processedAllStoredValues) {
      newValueRef.set(NO_VALUE);
      processedAll = processor.process(NO_VALUE, newValueRef);
      valueToInsert = newValueRef.get();
    }

    for (var index = modifications.size() - 1; index >= 0; index--) {
      var modification = modifications.get(index);
      if (modification.newValue() == NO_VALUE) {
        values.removeLong(modification.index());
        size--;
      }
      else {
        values.set(modification.index(), modification.newValue());
      }
    }
    if (values != null && values.isEmpty()) {
      valuesByKey.remove(key);
    }
    if (valueToInsert != NO_VALUE) {
      requireValue(valueToInsert);
      valuesByKey.computeIfAbsent(key, _ -> new LongArrayList()).add(valueToInsert);
      size++;
    }
    return processedAll;
  }

  @Override
  public boolean remove(int key, long value) {
    requireValue(value);
    var values = valuesByKey.get(key);
    if (values == null || !values.rem(value)) {
      return false;
    }
    size--;
    if (values.isEmpty()) {
      valuesByKey.remove(key);
    }
    return true;
  }

  @Override
  public boolean forEach(@NotNull KeyValueProcessor processor) throws IOException {
    for (var entry : valuesByKey.int2ObjectEntrySet()) {
      var key = entry.getIntKey();
      var values = entry.getValue();
      for (var index = 0; index < values.size(); index++) {
        if (!processor.process(key, values.getLong(index))) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public void clear() {
    valuesByKey.clear();
    size = 0;
  }

  private static void requireValue(long value) {
    if (value == NO_VALUE) {
      throw new IllegalArgumentException("The value must not use the reserved zero value");
    }
  }
}
