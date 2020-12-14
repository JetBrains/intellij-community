// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;

public final class CompactDataOutput implements DataOutput {
  private final OutputStream out;
  private final byte[] writeBuffer = IOUtil.allocReadWriteUTFBuffer();

  public CompactDataOutput(OutputStream out) {
    this.out = out;
  }

  @Override
  public void write(int b) throws IOException {
    out.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    out.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
  }

  @Override
  public void writeBoolean(boolean v) throws IOException {
    out.write(v ? 1 : 0);
  }

  @Override
  public void writeByte(int v) throws IOException {
    out.write(v);
  }

  @Override
  public void writeShort(int v) throws IOException {
    out.write((v >>> 8) & 0xFF);
    out.write(v & 0xFF);
  }

  @Override
  public void writeChar(int v) throws IOException {
    out.write((v >>> 8) & 0xFF);
    out.write(v & 0xFF);
  }

  @Override
  public void writeInt(int val) throws IOException {
    DataInputOutputUtil.writeINT(this, val);
  }

  @Override
  public void writeLong(long v) throws IOException { // TODO: Make longs actually compact
    writeBuffer[0] = (byte)(v >>> 56);
    writeBuffer[1] = (byte)(v >>> 48);
    writeBuffer[2] = (byte)(v >>> 40);
    writeBuffer[3] = (byte)(v >>> 32);
    writeBuffer[4] = (byte)(v >>> 24);
    writeBuffer[5] = (byte)(v >>> 16);
    writeBuffer[6] = (byte)(v >>>  8);
    writeBuffer[7] = (byte)(v >>>  0);
    out.write(writeBuffer, 0, 8);
  }

  @Override
  public void writeFloat(float v) throws IOException {
    writeInt(Float.floatToIntBits(v));
  }

  @Override
  public void writeDouble(double v) throws IOException {
    writeLong(Double.doubleToLongBits(v));
  }

  @Override
  public void writeBytes(String s) throws IOException {
    int len = s.length();
    for (int i = 0 ; i < len ; i++) {
        out.write((byte)s.charAt(i));
    }
  }

  @Override
  public void writeChars(String s) throws IOException {
    int len = s.length();
    for (int i = 0 ; i < len ; i++) {
        int v = s.charAt(i);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }
  }

  @Override
  public void writeUTF(String s) throws IOException {
    IOUtil.writeUTFFast(writeBuffer, this, s);
  }
}
