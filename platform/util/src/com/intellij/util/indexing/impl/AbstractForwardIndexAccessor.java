// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractForwardIndexAccessor<Key, Value, Data, Input> implements ForwardIndexAccessor<Key, Value, Input> {
  private final DataExternalizer<Data> myDataExternalizer;

  protected AbstractForwardIndexAccessor(@NotNull DataExternalizer<Data> externalizer) {
    myDataExternalizer = externalizer;
  }

  @Nullable
  protected abstract Data convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input input);

  protected abstract Collection<Key> getKeysFromData(@Nullable Data data);

  public Data getData(@Nullable ByteArraySequence bytes) throws IOException {
    if (bytes == null) return null;
    DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getLength()));
    return myDataExternalizer.read(stream);
  }

  @Nullable
  @Override
  public Collection<Key> getKeys(ByteArraySequence bytes) throws IOException {
    return getKeysFromData(getData(bytes));
  }

  private static final ThreadLocalCachedByteArray ourSpareByteArray = new ThreadLocalCachedByteArray();
  @Nullable
  @Override
  public ByteArraySequence serialize(@Nullable Map<Key, Value> map, @Nullable Input input) throws IOException {
    if (map == null || map.isEmpty()) return null;
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(ourSpareByteArray.getBuffer(4 * map.size()));
    DataOutputStream stream = new DataOutputStream(out);
    myDataExternalizer.save(stream, convertToDataType(map, input));
    return out.toByteArraySequence();
  }
}
