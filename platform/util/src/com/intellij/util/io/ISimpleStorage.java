package com.intellij.util.io;

import com.intellij.openapi.Forceable;

import java.io.Closeable;

interface ISimpleStorage extends Closeable, Forceable {
  void put(int index, byte value);
  byte get(int index);

  void putInt(int index, int value);
  int getInt(int index);

  long length();

  void put(int pos, byte[] buf, int offset, int length);
  void get(int pos, byte[] buf, int offset, int length);

  void putLong(int pos, long value);
  long getLong(int pos);
}
