// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

public class MapDataExternalizer<K, V> implements DataExternalizer<Map<K, V>> {
  private final DataExternalizer<K> myKeyExternalizer;
  private final DataExternalizer<V> myValueExternalizer;

  public MapDataExternalizer(DataExternalizer<K> externalizer, DataExternalizer<V> valueExternalizer) {
    myKeyExternalizer = externalizer;
    myValueExternalizer = valueExternalizer;
  }

  @Override
  public void save(@NotNull DataOutput out, Map<K, V> map) throws IOException {
    DataInputOutputUtil.writeINT(out, map.size());
    for (Map.Entry<K, V> entry : map.entrySet()) {
      myKeyExternalizer.save(out, entry.getKey());
      myValueExternalizer.save(out, entry.getValue());
    }
  }

  @Override
  public Map<K, V> read(@NotNull DataInput in) throws IOException {
    int capacity = DataInputOutputUtil.readINT(in);
    Map<K, V> result = new THashMap<K, V>(capacity);
    for (int i = 0; i < capacity; i++) {
      result.put(myKeyExternalizer.read(in), myValueExternalizer.read(in));
    }
    return result;
  }
}
