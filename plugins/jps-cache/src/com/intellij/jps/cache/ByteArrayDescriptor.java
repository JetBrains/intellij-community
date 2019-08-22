package com.intellij.jps.cache;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ByteArrayDescriptor implements DataExternalizer<byte[]> {
  public static final ByteArrayDescriptor INSTANCE = new ByteArrayDescriptor();

  private ByteArrayDescriptor() {}

  @Override
  public void save(@NotNull DataOutput out, byte[] value) throws IOException {
    out.writeInt(value.length);
    for (int i = 0; i < value.length; ++i) {
      out.writeByte(value[i]);
    }
  }

  @Override
  public byte[] read(@NotNull DataInput in) throws IOException {
    int size = in.readInt();
    byte[] buffer = new byte[size];
    for (int i = 0; i < size; ++i) {
      buffer[i] = in.readByte();
    }

    return buffer;
  }
}
