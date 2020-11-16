/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentStringEnumerator extends PersistentEnumerator<String> implements AbstractStringEnumerator {
  @Nullable private final CachingEnumerator<String> myCache;

  public PersistentStringEnumerator(@NotNull Path file) throws IOException {
    this(file, null);
  }

  public PersistentStringEnumerator(@NotNull Path file, @Nullable StorageLockContext storageLockContext) throws IOException {
    this(file, 1024 * 4, storageLockContext);
  }

  public PersistentStringEnumerator(@NotNull Path file, boolean cacheLastMappings) throws IOException {
    this(file, 1024 * 4, cacheLastMappings, null);
  }

  public PersistentStringEnumerator(@NotNull Path file, final int initialSize) throws IOException {
    this(file, initialSize, null);
  }

  public PersistentStringEnumerator(@NotNull Path file,
                                    final int initialSize,
                                    @Nullable StorageLockContext lockContext) throws IOException {
    this(file, initialSize, false, lockContext);
  }

  public PersistentStringEnumerator(@NotNull Path file,
                                     final int initialSize,
                                     boolean cacheLastMappings,
                                     @Nullable StorageLockContext lockContext) throws IOException {
    super(file, EnumeratorStringDescriptor.INSTANCE, initialSize, lockContext);
    myCache = cacheLastMappings ? new CachingEnumerator<>(new DataEnumerator<String>() {
      @Override
      public int enumerate(@Nullable String value) throws IOException {
        return PersistentStringEnumerator.super.enumerate(value);
      }

      @Nullable
      @Override
      public String valueOf(int idx) throws IOException {
        return PersistentStringEnumerator.super.valueOf(idx);
      }
    }, EnumeratorStringDescriptor.INSTANCE) : null;
  }

  @Override
  public int enumerate(@Nullable String value) throws IOException {
    return myCache != null ? myCache.enumerate(value) : super.enumerate(value);
  }

  @Nullable
  @Override
  public String valueOf(int idx) throws IOException {
    return myCache != null ? myCache.valueOf(idx) : super.valueOf(idx);
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (myCache != null) myCache.close();
  }
}
