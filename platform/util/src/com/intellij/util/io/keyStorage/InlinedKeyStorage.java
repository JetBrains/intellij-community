// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;

import com.intellij.util.io.InlineKeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * {@link AppendableObjectStorage} implementation for values that could be bijectively mapped
 * to-from int, see {@link InlineKeyDescriptor}.
 * valueId == value itself converted to int
 */
public final class InlinedKeyStorage<Data> implements AppendableObjectStorage<Data> {

  private final InlineKeyDescriptor<Data> myDescriptor;

  public InlinedKeyStorage(@NotNull InlineKeyDescriptor<Data> descriptor) {
    myDescriptor = descriptor;
  }

  @Override
  public Data read(int valueId, boolean checkAccess) throws IOException {
    return myDescriptor.fromInt(valueId);
  }

  @Override
  public boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int append(Data value) throws IOException {
    return myDescriptor.toInt(value);
  }

  @Override
  public boolean checkBytesAreTheSame(int valueId, Data value) {
    return false;
  }

  @Override
  public void clear() throws IOException {
    //do nothing
  }

  @Override
  public void lockRead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unlockRead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lockWrite() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unlockWrite() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentLength() {
    return -1;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void force() {

  }

  @Override
  public void close() throws IOException {

  }
}
