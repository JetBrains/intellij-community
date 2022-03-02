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

import com.intellij.openapi.util.Ref;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentSetImpl<T> extends PersistentEnumerator<T> implements PersistentSet<T> {

  public PersistentSetImpl(@NotNull Path file,
                           @NotNull KeyDescriptor<T> dataDescriptor,
                           int initialSize,
                           @Nullable StorageLockContext lockContext, int version) throws IOException {
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
  public boolean isEmpty() throws IOException {
    Ref<Boolean> isEmpty = Ref.create(true);
    myEnumerator.traverseAllRecords(new PersistentEnumeratorBase.RecordsProcessor() {
      @Override
      public boolean process(int record) {
        isEmpty.set(false);
        return false;
      }
    });
    return isEmpty.get();
  }

  @Override
  public void flush() {
    force();
  }
}
