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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class PersistentEnumeratorDelegate<Data> implements Closeable, Forceable {
  @NotNull protected final PersistentEnumeratorBase<Data> myEnumerator;

  public PersistentEnumeratorDelegate(@NotNull final File file, @NotNull KeyDescriptor<Data> dataDescriptor, final int initialSize) throws IOException {
    this(file, dataDescriptor, initialSize, null);
  }

  public PersistentEnumeratorDelegate(@NotNull final File file,
                                      @NotNull KeyDescriptor<Data> dataDescriptor,
                                      final int initialSize,
                                      @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
    myEnumerator = useBtree() ? new PersistentBTreeEnumerator<Data>(file, dataDescriptor, initialSize, lockContext) :
                   new PersistentEnumerator<Data>(file, dataDescriptor, initialSize);
  }

  static boolean useBtree() {
    String property = System.getProperty("idea.use.btree");
    return !"false".equals(property);
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
    synchronized (myEnumerator) {
      myEnumerator.markDirty(true);
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

  public Data valueOf(int id) throws IOException {
    return myEnumerator.valueOf(id);
  }

  public int enumerate(Data name) throws IOException {
    return myEnumerator.enumerate(name);
  }

  public int tryEnumerate(Data name) throws IOException {
    return myEnumerator.tryEnumerate(name);
  }

  public boolean traverseAllRecords(PersistentEnumeratorBase.RecordsProcessor recordsProcessor) throws IOException {
    return myEnumerator.traverseAllRecords(recordsProcessor);
  }

  public Collection<Data> getAllDataObjects(@Nullable final PersistentEnumeratorBase.DataFilter filter) throws IOException {
    return myEnumerator.getAllDataObjects(filter);
  }
}
