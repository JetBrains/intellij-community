// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.locks.Lock;

public class PersistentEnumerator<Data> implements DurableDataEnumerator<Data>,
                                                   ScannableDataEnumeratorEx<Data> {
  protected final @NotNull PersistentEnumeratorBase<Data> myEnumerator;

  public PersistentEnumerator(@NotNull Path file, @NotNull KeyDescriptor<Data> dataDescriptor, final int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentEnumerator(final @NotNull Path file,
                              @NotNull KeyDescriptor<Data> dataDescriptor,
                              final int initialSize,
                              @Nullable StorageLockContext lockContext) throws IOException {
    myEnumerator = new PersistentBTreeEnumerator<>(file, dataDescriptor, initialSize, lockContext);
  }

  public PersistentEnumerator(final @NotNull File file,
                              @NotNull KeyDescriptor<Data> dataDescriptor,
                              final int initialSize,
                              @Nullable StorageLockContext lockContext,
                              int version) throws IOException {
    this(file.toPath(), dataDescriptor, initialSize, lockContext, version);
  }

  public PersistentEnumerator(@NotNull Path file,
                              @NotNull KeyDescriptor<Data> dataDescriptor,
                              final int initialSize,
                              @Nullable StorageLockContext lockContext,
                              int version) throws IOException {
    myEnumerator = createDefaultEnumerator(file, dataDescriptor, initialSize, lockContext, version, true);
  }

  static @NotNull <Data> PersistentEnumeratorBase<Data> createDefaultEnumerator(@NotNull Path file,
                                                                                @NotNull KeyDescriptor<Data> dataDescriptor,
                                                                                final int initialSize,
                                                                                @Nullable StorageLockContext lockContext,
                                                                                int version,
                                                                                boolean registerForStats) throws IOException {
    return new PersistentBTreeEnumerator<>(file, dataDescriptor, initialSize, lockContext, version, false, registerForStats);
  }

  @ApiStatus.Internal
  public static int getVersion() {
    return PersistentBTreeEnumerator.baseVersion();
  }

  @Override
  public void close() throws IOException {
    final PersistentEnumeratorBase<Data> enumerator = myEnumerator;
    //noinspection ConstantConditions
    if (enumerator != null) {
      enumerator.close();
    }
  }

  public boolean isClosed() {
    return myEnumerator.isClosed();
  }

  @Override
  public boolean isDirty() {
    return myEnumerator.isDirty();
  }

  public final void markDirty() throws IOException {
    Lock lock = myEnumerator.getWriteLock();
    lock.lock();
    try {
      myEnumerator.markDirty(true);
    }
    finally {
      lock.unlock();
    }
  }

  public boolean isCorrupted() {
    return myEnumerator.isCorrupted();
  }

  public void markCorrupted() {
    myEnumerator.markCorrupted();
  }

  @Override
  public void force() {
    myEnumerator.force();
  }

  @Override
  public Data valueOf(@Range(from = 1, to = Integer.MAX_VALUE) int id) throws IOException {
    return myEnumerator.valueOf(id);
  }

  @Override
  public @Range(from = 1, to = Integer.MAX_VALUE) int enumerate(Data name) throws IOException {
    return myEnumerator.enumerate(name);
  }

  @Override
  public int tryEnumerate(Data name) throws IOException {
    return myEnumerator.tryEnumerate(name);
  }

  @ApiStatus.Internal
  public Collection<Data> getAllDataObjects(final @Nullable PersistentEnumeratorBase.DataFilter filter) throws IOException {
    return myEnumerator.getAllDataObjects(filter);
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super Data> reader) throws IOException {
    return myEnumerator.forEach(reader);
  }

  @Override
  public int recordsCount() throws IOException {
    return myEnumerator.recordsCount();
  }
}
