// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;

@ApiStatus.Experimental
public abstract class AbstractForwardIndexAccessor<Key, Value, DataType> implements ForwardIndexAccessor<Key, Value> {
  @NotNull
  private final DataExternalizer<DataType> myDataTypeExternalizer;

  public AbstractForwardIndexAccessor(@NotNull DataExternalizer<DataType> externalizer) {
    myDataTypeExternalizer = externalizer;
  }

  protected abstract InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable DataType inputData) throws IOException;

  @Nullable
  public DataType deserializeData(@Nullable ByteArraySequence sequence) throws IOException {
    if (sequence == null) return null;
    return deserializeFromByteSeq(sequence, myDataTypeExternalizer);
  }

  @NotNull
  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return createDiffBuilder(inputId, deserializeData(sequence));
  }

  @Nullable
  public abstract DataType convertToDataType(@NotNull InputData<Key, Value> data);

  @Nullable
  @Override
  public ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException {
    return serializeIndexedData(convertToDataType(data));
  }

  @Nullable
  public ByteArraySequence serializeIndexedData(@Nullable DataType data) throws IOException {
    if (data == null) return null;
    return serializeToByteSeq(data, myDataTypeExternalizer, getBufferInitialSize(data));
  }

  protected int getBufferInitialSize(@NotNull DataType dataType) {
    return 4;
  }

  private static final ThreadLocalCachedByteArray ourSpareByteArray = new ThreadLocalCachedByteArray();
  public static <Data> ByteArraySequence serializeToByteSeq(@NotNull Data data,
                                                            @NotNull DataExternalizer<Data> externalizer,
                                                            int bufferInitialSize) throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(ourSpareByteArray.getBuffer(bufferInitialSize));
    DataOutputStream stream = new DataOutputStream(out);
    externalizer.save(stream, data);
    return out.toByteArraySequence();
  }

  public static <Data> Data deserializeFromByteSeq(@NotNull ByteArraySequence bytes,
                                                   @NotNull DataExternalizer<Data> externalizer) throws IOException {
    DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getLength()));
    return externalizer.read(stream);
  }
}
