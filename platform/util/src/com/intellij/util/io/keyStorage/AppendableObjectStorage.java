// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.Forceable;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;

/** Every single method call must be guarded by lockRead/lockWrite -- including .close() and .force()! */
public interface AppendableObjectStorage<Data> extends Forceable, Closeable {
  Data read(int addr, boolean checkAccess) throws IOException;

  boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException;

  int append(Data value) throws IOException;

  boolean checkBytesAreTheSame(int addr, Data value) throws IOException;

  void clear() throws IOException;

  //FIXME RC: there is inconsistency in how to use those locking method: 'original' AOS usage in PersistentEnumeratorBase
  //          doesn't use them at all -- instead it acquires StorageLockingContext lock upper the stack. But newer usages
  //          in e.g. KeyHashLog -- use those methods for acquiring same lock. So it is really a leaking abstraction: one
  //          need to know those locking methods really acquire PagedStorage lock, which is really a shared StorageLockingContext
  //          lock, which could be acquired via other PagedStorage instance.
  void lockRead();

  void unlockRead();

  void lockWrite();

  void unlockWrite();

  int getCurrentLength();

  @FunctionalInterface
  interface StorageObjectProcessor<Data> {
    boolean process(int offset, Data data);
  }
}
