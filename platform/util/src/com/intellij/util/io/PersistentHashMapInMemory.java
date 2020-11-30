// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PersistentHashMapInMemory<Key, Value> implements PersistentHashMapBase<Key, Value> {
  private final Object myLock = new Object();

  private final ConcurrentHashMap<Key, Value> myMap = new ConcurrentHashMap<>();
  private final AtomicBoolean myIsClosed = new AtomicBoolean(false);
  private final AtomicBoolean myIsDirty = new AtomicBoolean(false);

  @Override
  public boolean isCorrupted() {
    return false;
  }

  public @NotNull Object getDataAccessLock() {
    return myLock;
  }

  @Override
  public void dropMemoryCaches() {

  }

  @Override
  public void deleteMap() {
    myMap.clear();
  }

  @Override
  public void put(Key key, Value value) throws IOException {
    myMap.put(key, value);
  }

  @Override
  public void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    //TODO: how could we append data? Why binary only?
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return processKeysWithExistingMapping(processor);
  }

  @Override
  public boolean isDirty() {
    return myIsDirty.get();
  }

  @Override
  public void markDirty() throws IOException {
    myIsDirty.set(true);
  }

  @Override
  public @NotNull Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    return myMap.keySet();
  }

  @Override
  public boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException {
    for (Key key : getAllKeysWithExistingMapping()) {
      if (!processor.process(key)) return false;
    }
    return true;
  }

  @Override
  public Value get(Key key) throws IOException {
    return myMap.get(key);
  }

  @Override
  public boolean containsMapping(Key key) throws IOException {
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
