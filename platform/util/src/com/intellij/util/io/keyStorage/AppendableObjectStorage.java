// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.Forceable;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;

/**
 * Every single method call must be guarded by lockRead/lockWrite -- including .close() and .force()!
 * <p>
 * TODO RC: there is inconsistency in interpreting offset/ids by this class: from the usage, all
 * int params/return values here are kind of 'id'. I.e. append(value) returns valueId, something
 * that could be used to access value later on -- read(valueId) it back, checkBytesAreTheSame(valueId, value),
 * enumerate all values with processAll(). But all apt parameters are named 'addr' or 'offset',
 * which implies it is physical offset in some storage -- which is inconsistent, especially because
 * there are implementations there valueId is an id itself (see {@link InlinedKeyStorage})
 */
public interface AppendableObjectStorage<Data> extends Forceable, Closeable {

  Data read(int valueId, boolean checkAccess) throws IOException;

  /**
   * List all the items appended to the storage before this method invocation. Returns true if all items were supplied to
   * the processor, false if the processor stops iteration early.
   * Counter-intuitively, but one MUST NOT hold read-lock ({@link #lockRead}) for calling this method
   * (can hold write-lock though).
   * This is highly implementation-dependent (e.g. {@link #read(int, boolean)} needs read-lock, as
   * intuition suggests), and it was done specifically to support few especially deadlock-prone
   * scenarios
   */
  boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException;

  /** @return ID of value appended, by which value could be referred later on */
  int append(Data value) throws IOException;

  boolean checkBytesAreTheSame(int valueId, Data value) throws IOException;

  void clear() throws IOException;

  //FIXME RC: there is inconsistency in how to use those locking methods: 'original' AOS usage in PersistentEnumeratorBase
  //          doesn't use them at all -- instead PersistentEnumeratorBase acquires StorageLockingContext lock upper the stack.
  //          But newer usages in e.g. KeyHashLog -- use those methods for acquiring the same lock.
  //          So it is really a leaking abstraction: one need to know those locking methods really acquire PagedStorage lock,
  //          which is really a shared StorageLockingContext lock, which could be acquired via other PagedStorage instance.
  void lockRead();

  void unlockRead();

  void lockWrite();

  void unlockWrite();

  int getCurrentLength();

  @FunctionalInterface
  interface StorageObjectProcessor<Data> {
    boolean process(int valueId, Data value) throws IOException;
  }
}
