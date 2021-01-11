// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.index;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.*;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.DataUtil;
import org.jetbrains.mvstore.type.*;

class DataExternalizerDataTypeConverter {
  @SuppressWarnings("unchecked")
  @NotNull
  static <T> DataType<T> convert(@NotNull DataExternalizer<T> externalizer) {
    if (externalizer instanceof IntInlineKeyDescriptor) {
      return (DataType<T>)IntDataType.INSTANCE;
    }
    if (externalizer instanceof EnumeratorStringDescriptor) {
      return (DataType<T>)StringDataType.INSTANCE;
    }
    if (externalizer instanceof ByteSequenceDataExternalizer) {
      return (DataType<T>)ByteSequenceDataType.INSTANCE;
    }
    throw new IllegalArgumentException("unsupported externalizer");
  }

  @NotNull
  static <T> KeyableDataType<T> convert(@NotNull KeyDescriptor<T> descriptor) {
    DataType<T> dataType = convert((DataExternalizer<T>)descriptor);
    return (KeyableDataType<T>)dataType;
  }

  private static final class ByteSequenceDataType implements DataType<ByteArraySequence> {
    public static final ByteSequenceDataType INSTANCE = new ByteSequenceDataType();
    @Override
    public int getMemory(ByteArraySequence obj) {
      return DataUtil.VAR_INT_MAX_SIZE + obj.getLength();
    }

    @Override
    public int getFixedMemory() {
      return -1;
    }

    @Override
    public void write(ByteBuf buf, ByteArraySequence obj) {
      ByteArrayDataType.INSTANCE.write(buf, obj.toBytes());
    }

    @Override
    public ByteArraySequence read(ByteBuf buff) {
      return ByteArraySequence.create(ByteArrayDataType.INSTANCE.read(buff));
    }

    @Override
    public ByteArraySequence[] createStorage(int size) {
      return new ByteArraySequence[size];
    }
  }
}
