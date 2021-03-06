// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class CompactDataInput implements DataInput {
  private final InputStream in;
  private final byte[] readBuffer = IOUtil.allocReadWriteUTFBuffer();

  public CompactDataInput(InputStream in) {
    this.in = in;
  }

  @Override
  public void readFully(byte @NotNull [] b) throws IOException {
    readFully(b, 0, b.length);
  }

  @Override
  public void readFully(byte @NotNull [] b, int off, int len) throws IOException {
    if (len < 0)
        throw new IndexOutOfBoundsException();
    int n = 0;
    while (n < len) {
        int count = in.read(b, off + n, len - n);
        if (count < 0)
            throw new EOFException();
        n += count;
    }
  }

  @Override
  public int skipBytes(int n) throws IOException {
    int total = 0;
    int cur = 0;

    while ((total<n) && ((cur = (int) in.skip(n-total)) > 0)) {
        total += cur;
    }

    return total;
  }

  @Override
  public boolean readBoolean() throws IOException {
    int ch = in.read();
    if (ch < 0)
        throw new EOFException();
    return (ch != 0);
  }

  @Override
  public byte readByte() throws IOException {
    int ch = in.read();
    if (ch < 0)
        throw new EOFException();
    return (byte)(ch);
  }

  @Override
  public int readUnsignedByte() throws IOException {
    int ch = in.read();
    if (ch < 0)
        throw new EOFException();
    return ch;
  }

  @Override
  public short readShort() throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    if ((ch1 | ch2) < 0)
        throw new EOFException();
    return (short)((ch1 << 8) + (ch2 << 0));
  }

  @Override
  public int readUnsignedShort() throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    if ((ch1 | ch2) < 0)
        throw new EOFException();
    return (ch1 << 8) + (ch2 << 0);
  }

  @Override
  public char readChar() throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    if ((ch1 | ch2) < 0)
        throw new EOFException();
    return (char)((ch1 << 8) + (ch2 << 0));
  }

  @Override
  public int readInt() throws IOException {
    return DataInputOutputUtil.readINT(this);
  }

  @Override
  public long readLong() throws IOException { // TODO: Make longs actually compact
    readFully(readBuffer, 0, 8);
    return (((long)readBuffer[0] << 56) +
            ((long)(readBuffer[1] & 255) << 48) +
            ((long)(readBuffer[2] & 255) << 40) +
            ((long)(readBuffer[3] & 255) << 32) +
            ((long)(readBuffer[4] & 255) << 24) +
            ((readBuffer[5] & 255) << 16) +
            ((readBuffer[6] & 255) <<  8) +
            ((readBuffer[7] & 255) <<  0));
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
  public String readLine() {
    throw new UnsupportedOperationException("readLine is not implemented!");
  }

  @NotNull
  @Override
  public String readUTF() throws IOException {
    return IOUtil.readUTFFast(readBuffer, this);
  }
}
