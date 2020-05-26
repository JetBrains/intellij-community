// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.Forceable;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;

public interface AppendableObjectStorage<Data> extends Forceable, Closeable {
  Data read(int addr) throws IOException;

  boolean processAll(@NotNull Processor<? super Data> processor) throws IOException;

  int append(Data value) throws IOException;

  boolean checkBytesAreTheSame(int addr, Data value) throws IOException;

  void lockRead();

  void unlockRead();

  void lockWrite();

  void unlockWrite();

  int getCurrentLength();
}
