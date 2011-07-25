package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

class RandomAccessFileSimpleStorage implements ISimpleStorage {
  private final RandomAccessDataFile storage;

  public RandomAccessFileSimpleStorage(File file, PagePool pool) throws IOException {
    FileUtil.createIfDoesntExist(file);
    storage = new RandomAccessDataFile(file, pool);
  }

  @Override
  public void put(int index, byte value) {
    storage.putByte(index, value);
  }

  @Override
  public byte get(int index) {
    return storage.getByte(index);
  }

  @Override
  public void putInt(int index, int value) {
    storage.putInt(index, value);
  }

  @Override
  public int getInt(int index) {
    return storage.getInt(index);
  }

  @Override
  public void putLong(int pos, long value) {
    storage.putLong(pos, value);
  }

  @Override
  public long getLong(int pos) {
    return storage.getLong(pos);
  }

  @Override
  public long length() {
    return storage.length();
  }

  @Override
  public void put(int pos, byte[] buf, int offset, int length) {
    storage.put(pos, buf, offset, length);
  }

  @Override
  public void get(int pos, byte[] buf, int offset, int length) {
    storage.get(pos, buf, offset, length);
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public boolean isDirty() {
    return storage.isDirty();
  }

  @Override
  public void force() {
    storage.force();
  }
}
