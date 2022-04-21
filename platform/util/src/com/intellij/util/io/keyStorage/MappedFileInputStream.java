// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.io.keyStorage;

import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;

final class MappedFileInputStream extends InputStream implements DataInput {
  private final ResizeableMappedFile raf;
  private final long limit;
  private long cur;

  MappedFileInputStream(@NotNull ResizeableMappedFile raf, final long pos, final long limit) {
    this.raf = raf;
    this.cur = pos;
    this.limit = limit;
    //TODO we may cache buffer for performance reasons
  }

  @Override
  public int available() {
    return (int)(limit - cur);
  }

  @Override
  public void close() {
    //do nothing because we want to leave the random access file open.
  }

  @Override
  public int read() throws IOException {
    int retval = -1;
    if (cur < limit) {
      retval = raf.get(cur++);
    }
    return retval;
  }

  @Override
  public int read(byte @NotNull [] b, int offset, int length) throws IOException {
    //only allow a read of the amount available.
    if (length > available()) {
      length = available();
    }

    if (available() > 0) {
      raf.get(cur, b, offset, length);
      cur += length;
    }

    return length;
  }

  @Override
  public long skip(long amountToSkip) {
    long amountSkipped = Math.min(amountToSkip, available());
    cur += amountSkipped;
    return amountSkipped;
  }

  @Override
  public void readFully(byte[] b) throws IOException {
    readFully(b, 0, b.length);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException {
    read(b, off, len);
  }

  @Override
  public int skipBytes(int n) {
    return (int)skip(n);
  }

  @Override
  public boolean readBoolean() throws IOException {
    return read() != 0;
  }

  @Override
  public byte readByte() throws IOException {
    return raf.get(cur++);
  }

  @Override
  public int readUnsignedByte() throws IOException {
    return Byte.toUnsignedInt(readByte());
  }

  @Override
  public short readShort() throws IOException {
    //TODO
    int ch1 = read();
    int ch2 = read();
    if ((ch1 | ch2) < 0) throw new EOFException();
    return (short)((ch1 << 8) + (ch2));
  }

  @Override
  public int readUnsignedShort() throws IOException {
    return Short.toUnsignedInt(readShort());
  }

  @Override
  public char readChar() throws IOException {
    return (char)readShort();
  }

  @Override
  public int readInt() throws IOException {
    int value = raf.getInt(cur);
    cur += 4;
    return value;
  }

  @Override
  public long readLong() throws IOException {
    long value = raf.getLong(cur);
    cur += 8;
    return value;
  }

  @Override
  public float readFloat() throws IOException {
    return Float.intBitsToFloat(readInt());
  }

  @Override
  public double readDouble() throws IOException {
    return Double.longBitsToDouble(readLong());
  }

  @Override
  @Deprecated
  public String readLine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String readUTF() throws IOException {
    return DataInputStream.readUTF(this);
  }
}
