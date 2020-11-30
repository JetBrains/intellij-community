// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.index;

import com.intellij.util.Processor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMapBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.MVStore;

import java.io.IOException;
import java.util.Collection;

import static org.jetbrains.mvstore.index.DataExternalizerDataTypeConverter.*;

public class MVStorePersistentHashMap<Key, Value> implements PersistentHashMapBase<Key, Value> {
  private final MVStore myStore;
  private final MVMap<Key, Value> myMap;

  public MVStorePersistentHashMap(@NotNull String mapName,
                                  @NotNull MVStore store,
                                  @NotNull KeyDescriptor<Key> keyDescriptor,
                                  @NotNull DataExternalizer<Value> valueExternalizer) {
    myStore = store;
    MVMap.Builder<Key, Value> builder = new MVMap
      .Builder<Key, Value>()
      .keyType(convert(keyDescriptor))
      .valueType(convert(valueExternalizer));
    myMap = myStore.openMap(mapName, builder);
  }

  @Override
  public void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    // TODO
  }

  @Override
  public Value get(Key key) throws IOException {
    return myMap.get(key);
  }

  @Override
  public void put(Key key, Value value) throws IOException {
    myMap.put(key, value);
  }

  @Override
  public void force() {
    myStore.commit();
  }

  @Override
  public void dropMemoryCaches() {
    //TODO
  }

  @Override
  public void deleteMap() {
    myStore.removeMap(myMap);
  }

  @Override
  public @NotNull Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    return myMap.keySet();
  }

  @Override
  public boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException {
    for (Key key : getAllKeysWithExistingMapping()) {
      if (!processor.process(key)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return processKeysWithExistingMapping(processor);
  }

  @Override
  public void remove(Key key) throws IOException {
    myMap.remove(key);
  }

  @Override
  public boolean containsMapping(Key key) throws IOException {
    return myMap.containsKey(key);
  }

  @Override
  public boolean isClosed() {
    return myMap.isClosed();
  }

  @Override
  public boolean isDirty() {
    //TODO
    return myStore.hasUnsavedChanges();
  }

  @Override
  public void markDirty() throws IOException {
    //TODO
  }

  @Override
  public boolean isCorrupted() {
    //TODO
    return false;
  }

  @Override
  public void close() throws IOException {
    //TODO
  }
}
