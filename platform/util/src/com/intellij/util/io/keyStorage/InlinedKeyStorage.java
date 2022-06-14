// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.keyStorage;

import com.intellij.util.io.InlineKeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class InlinedKeyStorage<Data> implements AppendableObjectStorage<Data> {

  private final InlineKeyDescriptor<Data> myDescriptor;

  public InlinedKeyStorage(@NotNull InlineKeyDescriptor<Data> descriptor) {
    myDescriptor = descriptor;
  }

  @Override
  public Data read(int addr, boolean checkAccess) throws IOException {
    return myDescriptor.fromInt(addr);
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
  public boolean checkBytesAreTheSame(int addr, Data value) {
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
