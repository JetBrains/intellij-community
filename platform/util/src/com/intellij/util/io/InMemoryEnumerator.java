// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.Processor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * In-memory (not persistent) implementation of {@linkplain ScannableDataEnumeratorEx} for
 * arbitrary data type. Useful as a building block for more elaborate impls, and for tests.
 * <br/>
 * <b>Not thread safe</b> -- external synchronization required for use in multithreaded environment.
 */
public class InMemoryEnumerator<Data> implements ScannableDataEnumeratorEx<Data> {

  private final Object2IntMap<Data> idByValue = new Object2IntOpenHashMap<>();
  private final Int2ObjectMap<Data> valueById = new Int2ObjectOpenHashMap<>();

  @Override
  public int enumerate(final @Nullable Data value) {
    int id = idByValue.getOrDefault(value, NULL_ID);
    if (id == NULL_ID) {
      id = valueById.size() + 1;
      valueById.put(id, value);
      idByValue.put(value, id);
    }
    return id;
  }

  @Override
  public int tryEnumerate(final @Nullable Data value) {
    return idByValue.getOrDefault(value, NULL_ID);
  }

  @Override
  public @Nullable Data valueOf(final int id) {
    return valueById.get(id);
  }

  @Override
  public boolean processAllDataObjects(final @NotNull Processor<? super Data> processor) {
    for (final Data value : idByValue.keySet()) {
      final boolean shouldContinue = processor.process(value);
      if (!shouldContinue) {
        return false;
      }
    }
    return true;
  }

  public Iterable<Data> enumeratedValues() {
    return new ArrayList<>(valueById.values());
  }

  /**
   * Registers in enumerator given value under given id. If such id is already in use for some value,
   * then {@link IllegalArgumentException} is thrown if overrideExisting=false, or previous mapping
   * just overridden if overrideExisting=true
   */
  public void registerValueWithGivenId(final int id,
                                       final Data value,
                                       final boolean overrideExisting) {
    if (!overrideExisting) {
      if (valueById.containsKey(id)) {
        throw new IllegalArgumentException(
          "id(=" + id + ") is already in use (value: " + valueById.get(id) + ") -- use overrideExisting=true");
      }
    }
    idByValue.put(value, id);
    valueById.put(id, value);
  }
}
