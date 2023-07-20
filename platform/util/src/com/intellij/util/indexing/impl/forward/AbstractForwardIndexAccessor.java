// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class AbstractForwardIndexAccessor<Key, Value, DataType> implements ForwardIndexAccessor<Key, Value> {
  private final @NotNull DataExternalizer<DataType> myDataTypeExternalizer;

  public AbstractForwardIndexAccessor(@NotNull DataExternalizer<DataType> externalizer) {
    myDataTypeExternalizer = externalizer;
  }

  protected abstract InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable DataType inputData) throws IOException;

  public @Nullable DataType deserializeData(@Nullable ByteArraySequence sequence) throws IOException {
    if (sequence == null) return null;
    return deserializeFromByteSeq(sequence, myDataTypeExternalizer);
  }

  @Override
  public @NotNull InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return createDiffBuilder(inputId, deserializeData(sequence));
  }

  public abstract @Nullable DataType convertToDataType(@NotNull InputData<Key, Value> data);

  @Override
  public @Nullable ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException {
    return serializeIndexedData(convertToDataType(data));
  }

  public @Nullable ByteArraySequence serializeIndexedData(@Nullable DataType data) throws IOException {
    if (data == null) return null;
    return serializeValueToByteSeq(data, myDataTypeExternalizer, getBufferInitialSize(data));
  }

  protected int getBufferInitialSize(@NotNull DataType dataType) {
    return 4;
  }

  private static final ThreadLocalCachedByteArray ourSpareByteArrayForKeys = new ThreadLocalCachedByteArray();
  private static final ThreadLocalCachedByteArray ourSpareByteArrayForValues = new ThreadLocalCachedByteArray();

  public static <Data> ByteArraySequence serializeKeyToByteSeq(/*must be not null if externalizer doesn't support nulls*/ Data data,
                                                            @NotNull DataExternalizer<Data> externalizer,
                                                            int bufferInitialSize) throws IOException {
    return serializeToByteSeq(data, externalizer, bufferInitialSize, ourSpareByteArrayForKeys);
  }

  public static <Data> ByteArraySequence serializeValueToByteSeq(/*must be not null if externalizer doesn't support nulls*/ Data data,
                                                            @NotNull DataExternalizer<Data> externalizer,
                                                            int bufferInitialSize) throws IOException {
    return serializeToByteSeq(data, externalizer, bufferInitialSize, ourSpareByteArrayForValues);
  }

  public static @Nullable <Data> ByteArraySequence serializeToByteSeq(Data data,
                                                                      @NotNull DataExternalizer<Data> externalizer,
                                                                      int bufferInitialSize,
                                                                      @NotNull ThreadLocalCachedByteArray cachedBufferToUse) throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(s -> {
      return cachedBufferToUse.getBuffer(s);
    }, bufferInitialSize);
    DataOutputStream stream = new DataOutputStream(out);
    externalizer.save(stream, data);
    return out.size() == 0 ? null : out.toByteArraySequence();
  }

  public static <Data> Data deserializeFromByteSeq(@NotNull ByteArraySequence bytes,
                                                   @NotNull DataExternalizer<Data> externalizer) throws IOException {
    return externalizer.read(bytes.toInputStream());
  }
}
