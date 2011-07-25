package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

final class MappedFileSimpleStorage implements ISimpleStorage {
  private final ResizeableMappedFile storage;

  public MappedFileSimpleStorage(File file, int initialSize) throws IOException {
    FileUtil.createIfDoesntExist(file);
    storage = new ResizeableMappedFile(file, initialSize, PersistentEnumeratorBase.ourLock);
  }

  public MappedFileSimpleStorage(File file, int initialSize, int pageSize, float growFactor, boolean zeroPagesWhenExpand) throws IOException {
    FileUtil.createIfDoesntExist(file);
    storage = new ResizeableMappedFile(file, initialSize, PersistentEnumeratorBase.ourLock, pageSize, growFactor, zeroPagesWhenExpand);
  }

  @Override
  public void put(int index, byte value) {
    storage.put(index, value);
  }

  @Override
  public byte get(int index) {
    return storage.get(index);
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
  public void putLong(int pos, long value) {
    storage.putLong(pos, value);
  }

  @Override
  public long getLong(int pos) {
    return storage.getLong(pos);
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
