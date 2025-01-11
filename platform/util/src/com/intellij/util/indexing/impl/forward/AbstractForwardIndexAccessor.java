// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Partial {@link ForwardIndexAccessor} implementation: splits {@link InputData} to/from {@link ByteArraySequence} conversion
 * into 2 steps: {@code InputData -> DataType <-> ByteArraySequence}
 * <p/>
 * Intermediate type DataType is a 'real type' -- i.e. something that is stored in ForwardIndex, and on that base
 * diff is calculated (this is why {@code InputData->DataType} conversion is defined, but no {@code DataType->InputData}
 * conversion is defined)
 */
@Internal
public abstract class AbstractForwardIndexAccessor<Key, Value, DataType> implements ForwardIndexAccessor<Key, Value> {
  private final @NotNull DataExternalizer<DataType> myDataTypeExternalizer;

  protected AbstractForwardIndexAccessor(@NotNull DataExternalizer<DataType> externalizer) {
    myDataTypeExternalizer = externalizer;
  }

  protected abstract InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable DataType inputData) throws IOException;

  protected abstract @Nullable DataType convertToDataType(@NotNull InputData<Key, Value> data);

  protected @Nullable ByteArraySequence serializeIndexedData(@Nullable DataType data) throws IOException {
    if (data == null) return null;
    return serializeValueToByteSeq(data, myDataTypeExternalizer, getBufferInitialSize(data));
  }

  public @Nullable DataType deserializeData(@Nullable ByteArraySequence sequence) throws IOException {
    if (sequence == null) return null;
    return deserializeFromByteSeq(sequence, myDataTypeExternalizer);
  }

  protected int getBufferInitialSize(@NotNull DataType dataType) {
    return 4;
  }


  @Override
  public @NotNull InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return createDiffBuilder(inputId, deserializeData(sequence));
  }

  @Override
  public @Nullable ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException {
    return serializeIndexedData(convertToDataType(data));
  }


  //======== helpers: ================================================================================================

  private static final ThreadLocalCachedByteArray ourSpareByteArrayForKeys = new ThreadLocalCachedByteArray();
  private static final ThreadLocalCachedByteArray ourSpareByteArrayForValues = new ThreadLocalCachedByteArray();

  public static <Data> ByteArraySequence serializeKeyToByteSeq(/*must be not null if externalizer doesn't support nulls*/ Data data,
                                                                                                                          @NotNull DataExternalizer<Data> externalizer,
                                                                                                                          int bufferInitialSize)
    throws IOException {
    return serializeToByteSeq(data, externalizer, bufferInitialSize, ourSpareByteArrayForKeys);
  }

  public static <Data> ByteArraySequence serializeValueToByteSeq(/*must be not null if externalizer doesn't support nulls*/ Data data,
                                                                                                                            @NotNull DataExternalizer<Data> externalizer,
                                                                                                                            int bufferInitialSize)
    throws IOException {
    return serializeToByteSeq(data, externalizer, bufferInitialSize, ourSpareByteArrayForValues);
  }

  public static @Nullable <Data> ByteArraySequence serializeToByteSeq(Data data,
                                                                      @NotNull DataExternalizer<Data> externalizer,
                                                                      int bufferInitialSize,
                                                                      @NotNull ThreadLocalCachedByteArray cachedBufferToUse)
    throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(s -> {
      return cachedBufferToUse.getBuffer(s);
    }, bufferInitialSize);
    DataOutputStream stream = new DataOutputStream(out);
    externalizer.save(stream, data);
    return out.size() == 0 ? null : out.toByteArraySequence();
  }

  public static <Data> Data deserializeFromByteSeq(@NotNull ByteArraySequence bytes,
                                                   @NotNull DataExternalizer<Data> externalizer) throws IOException {
    try (DataInputStream stream = bytes.toInputStream()) {
      Data data = externalizer.read(stream);

      int bytesLeft = stream.available();
      if (bytesLeft > 0) { //MAYBE RC: IOException or CorruptedException suits better?
        throw new IllegalStateException("stream is not read fully: " + bytesLeft + " bytes left out of " + bytes);
      }

      return data;
    }
  }
}
