/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.util;

import com.intellij.util.Processor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentBTreeEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class PersistentSetImpl<T> extends PersistentBTreeEnumerator<T> implements PersistentSet<T> {

  public PersistentSetImpl(@NotNull File file,
                           @NotNull KeyDescriptor<T> dataDescriptor,
                           int initialSize,
                           @Nullable PagedFileStorage.StorageLockContext lockContext, int version) throws IOException {
    super(file, dataDescriptor, initialSize, lockContext, version);
  }

  @Override
  public boolean contains(@NotNull T element) throws IOException {
    return tryEnumerate(element) != NULL_ID;
  }

  @Override
  public void put(@NotNull T element) throws IOException {
    enumerate(element);
  }

  @Override
  public void process(@NotNull Processor<? super T> processor) throws IOException {
    processAllDataObject(processor, null);
  }

  @Override
  public void flush() {
    force();
  }

  @Override
  public synchronized void markCorrupted() {
    super.markCorrupted();
  }
}
