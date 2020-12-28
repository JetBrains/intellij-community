// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.index;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IntInlineKeyDescriptor;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.DataType;
import org.h2.mvstore.type.StringDataType;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

class DataExternalizerDataTypeConverter {
  @NotNull
  static <T> DataType convert(@NotNull DataExternalizer<T> externalizer) {
    if (externalizer instanceof IntInlineKeyDescriptor) {
      return IntDataType.INSTANCE;
    }
    if (externalizer instanceof EnumeratorStringDescriptor) {
      return StringDataType.INSTANCE;
    }
    if (externalizer instanceof ByteSequenceDataExternalizer) {
      return ByteSequenceDataType.INSTANCE;
    }
    throw new IllegalArgumentException("unsupported externalizer");
  }

  private static final class ByteSequenceDataType implements DataType {
    public static final ByteSequenceDataType INSTANCE = new ByteSequenceDataType();

    @Override
    public int compare(Object a, Object b) {
      ByteArraySequence arrayA = (ByteArraySequence)a;
      ByteArraySequence arrayB = (ByteArraySequence)b;
      return Arrays.compare(arrayA.getInternalBuffer(), arrayA.getOffset(), arrayA.getOffset() + arrayA.getLength(),
                            arrayB.getInternalBuffer(), arrayB.getOffset(), arrayB.getOffset() + arrayB.getLength());
    }

    @Override
    public int getMemory(Object obj) {
      return Integer.BYTES + ((ByteArraySequence)obj).getLength();
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
      ByteArraySequence bas = (ByteArraySequence)obj;
      buff.putInt(bas.getLength());
      buff.put(bas.getInternalBuffer(), bas.getOffset(), bas.getLength());
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
      for (int i = 0; i < len; i++) {
        write(buff, obj[i]);
      }
    }

    @Override
    public Object read(ByteBuffer buff) {
      int size = buff.getInt();
      byte[] bytes = new byte[size];
      buff.get(bytes);
      return ByteArraySequence.create(bytes);
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
      for (int i = 0; i < len; i++) {
        obj[i] = read(buff);
      }
    }
  }

  private static class IntDataType implements DataType {
    private static final IntDataType INSTANCE = new IntDataType();

    @Override
    public int compare(Object a, Object b) {
      return Integer.compare((Integer)a, (Integer)b);
    }

    @Override
    public int getMemory(Object obj) {
      return Integer.BYTES;
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
      buff.putInt((Integer)obj);
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
      for (int i = 0; i < len; i++) {
        write(buff, obj[i]);
      }
    }

    @Override
    public Object read(ByteBuffer buff) {
      return buff.getInt();
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
      for (int i = 0; i < len; i++) {
        obj[i] = read(buff);
      }
    }
  }
}
