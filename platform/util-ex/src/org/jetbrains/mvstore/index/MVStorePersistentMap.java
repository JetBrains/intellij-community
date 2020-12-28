// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.index;

import com.intellij.util.Processor;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMapBase;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.jetbrains.mvstore.index.DataExternalizerDataTypeConverter.convert;

public class MVStorePersistentMap<Key, Value> implements PersistentMapBase<Key, Value> {
  private final MVStore myStore;
  private final DataExternalizer<Value> myValueExternalizer;
  private final MVMap<Key, Value> myMap;

  public MVStorePersistentMap(@NotNull String mapName,
                              @NotNull MVStore store,
                              @NotNull KeyDescriptor<Key> keyDescriptor,
                              @NotNull DataExternalizer<Value> valueExternalizer) {
    myStore = store;
    myValueExternalizer = valueExternalizer;
    MVMap.Builder<Key, Value> builder = new MVMap
      .Builder<Key, Value>()
      .keyType(convert(keyDescriptor))
      .valueType(convert(valueExternalizer));
    myMap = myStore.openMap(mapName, builder);
  }

  @Override
  public @NotNull DataExternalizer<Value> getValuesExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public void appendData(Key key, AppendablePersistentMap.@NotNull ValueDataAppender appender) throws IOException {
    throw new UnsupportedEncodingException();
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
  public void closeAndDelete() {
    myStore.removeMap(myMap);
  }

  @Override
  public boolean processExistingKeys(@NotNull Processor<? super Key> processor) throws IOException {
    for (Key key : myMap.keySet()) {
      if (!processor.process(key)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return processExistingKeys(processor);
  }

  @Override
  public void remove(Key key) throws IOException {
    myMap.remove(key);
  }

  @Override
  public boolean containsKey(Key key) throws IOException {
    return myMap.containsKey(key);
  }

  @Override
  public boolean isClosed() {
    return myMap.isClosed();
  }

  @Override
  public boolean isDirty() {
    return myStore.hasUnsavedChanges();
  }

  @Override
  public void markDirty() throws IOException {
    //TODO
  }

  @Override
  public void close() throws IOException {
    //TODO
  }
}
