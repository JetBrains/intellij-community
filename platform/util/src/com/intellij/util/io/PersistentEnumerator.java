/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.locks.Lock;

public class PersistentEnumerator<Data> implements DataEnumeratorEx<Data>, Closeable, Forceable {
  @NotNull protected final PersistentEnumeratorBase<Data> myEnumerator;

  public PersistentEnumerator(@NotNull Path file, @NotNull KeyDescriptor<Data> dataDescriptor, final int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentEnumerator(@NotNull final Path file,
                              @NotNull KeyDescriptor<Data> dataDescriptor,
                              final int initialSize,
                              @Nullable StorageLockContext lockContext) throws IOException {
    myEnumerator = new PersistentBTreeEnumerator<>(file, dataDescriptor, initialSize, lockContext);
  }

  public PersistentEnumerator(@NotNull final File file,
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
    myEnumerator = createDefaultEnumerator(file, dataDescriptor, initialSize, lockContext, version);
  }

  @NotNull
  static <Data> PersistentEnumeratorBase<Data> createDefaultEnumerator(@NotNull Path file,
                                                                       @NotNull KeyDescriptor<Data> dataDescriptor,
                                                                       final int initialSize,
                                                                       @Nullable StorageLockContext lockContext,
                                                                       int version) throws IOException {
    return new PersistentBTreeEnumerator<>(file, dataDescriptor, initialSize, lockContext, version, false);
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
  public Data valueOf(int id) throws IOException {
    return myEnumerator.valueOf(id);
  }

  @Override
  public int enumerate(Data name) throws IOException {
    return myEnumerator.enumerate(name);
  }

  @Override
  public int tryEnumerate(Data name) throws IOException {
    return myEnumerator.tryEnumerate(name);
  }

  @ApiStatus.Internal
  public Collection<Data> getAllDataObjects(@Nullable final PersistentEnumeratorBase.DataFilter filter) throws IOException {
    return myEnumerator.getAllDataObjects(filter);
  }

  @ApiStatus.Internal
  public boolean processAllDataObjects(@NotNull Processor<? super Data> processor) throws IOException {
    return myEnumerator.iterateData(processor);
  }
}
