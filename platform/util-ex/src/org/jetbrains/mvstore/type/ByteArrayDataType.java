/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import org.jetbrains.mvstore.DataUtil;

import java.util.Arrays;

public final class ByteArrayDataType implements KeyableDataType<byte[]> {
  public static final ByteArrayDataType INSTANCE = new ByteArrayDataType();

  private ByteArrayDataType() {
  }

  @Override
  public boolean equals(byte[] a, byte[] b) {
    return Arrays.equals(a, b);
  }

  @Override
  public int compare(byte[] a, byte[] b) {
    return Arrays.compare(a, b);
  }

  @Override
  public final byte[][] createStorage(int size) {
    return new byte[size][];
  }

  @Override
  public int getMemory(byte[] obj) {
    return DataUtil.VAR_INT_MAX_SIZE + obj.length;
  }

  @Override
  public int getFixedMemory() {
    return -1;
  }

  @Override
  public byte[] read(ByteBuf buf) {
    return DataUtil.readByteArray(buf);
  }

  @Override
  public void write(ByteBuf buf, byte[] value) {
    DataUtil.writeByteArray(buf, value);
  }
}

