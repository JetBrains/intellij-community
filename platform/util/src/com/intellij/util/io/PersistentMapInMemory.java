// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PersistentMapInMemory<Key, Value> implements PersistentMapBase<Key, Value> {
  private final Object myLock = new Object();

  private final ConcurrentHashMap<Key, Value> myMap = new ConcurrentHashMap<>();
  private final AtomicBoolean myIsClosed = new AtomicBoolean(false);
  private final DataExternalizer<Value> myValueExternalizer;

  public PersistentMapInMemory(@NotNull PersistentMapBuilder<Key,Value> builder) {
    myValueExternalizer = builder.getValueExternalizer();
  }

  @Override
  public @NotNull DataExternalizer<Value> getValuesExternalizer() {
    return myValueExternalizer;
  }

  public @NotNull Object getDataAccessLock() {
    return myLock;
  }

  @Override
  public void closeAndDelete() {
    myMap.clear();
  }

  @Override
  public void put(Key key, Value value) throws IOException {
    myMap.put(key, value);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return processExistingKeys(processor);
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void markDirty() throws IOException {
  }

  @Override
  public boolean processExistingKeys(@NotNull Processor<? super Key> processor) throws IOException {
    for (Key key : myMap.keySet()) {
      if (!processor.process(key)) return false;
    }
    return true;
  }

  @Override
  public Value get(Key key) throws IOException {
    return myMap.get(key);
  }

  @Override
  public boolean containsKey(Key key) throws IOException {
    return myMap.containsKey(key);
  }

  @Override
  public void remove(Key key) throws IOException {
    myMap.remove(key);
  }

  @Override
  public void force() {

  }

  @Override
  public boolean isClosed() {
    return myIsClosed.get();
  }

  @Override
  public void close() throws IOException {
    myIsClosed.set(true);
  }
}
