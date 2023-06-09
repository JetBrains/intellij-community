// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentCharSequenceEnumerator extends PersistentEnumerator<CharSequence> {
  private final @Nullable CachingEnumerator<CharSequence> myCache;

  public PersistentCharSequenceEnumerator(@NotNull Path file) throws IOException {
    this(file, null);
  }

  public PersistentCharSequenceEnumerator(@NotNull Path file, @Nullable StorageLockContext storageLockContext) throws IOException {
    this(file, 1024 * 4, storageLockContext);
  }

  public PersistentCharSequenceEnumerator(@NotNull Path file, boolean cacheLastMappings) throws IOException {
    this(file, 1024 * 4, cacheLastMappings, null);
  }

  public PersistentCharSequenceEnumerator(@NotNull Path file, final int initialSize) throws IOException {
    this(file, initialSize, null);
  }

  public PersistentCharSequenceEnumerator(@NotNull Path file,
                                    final int initialSize,
                                    @Nullable StorageLockContext lockContext) throws IOException {
    this(file, initialSize, false, lockContext);
  }

  public PersistentCharSequenceEnumerator(@NotNull Path file,
                                    final int initialSize,
                                    boolean cacheLastMappings,
                                    @Nullable StorageLockContext lockContext) throws IOException {
    super(file, EnumeratorCharSequenceDescriptor.INSTANCE, initialSize, lockContext);
    myCache = cacheLastMappings ? new CachingEnumerator<>(new DataEnumerator<CharSequence>() {
      @Override
      public int enumerate(@Nullable CharSequence value) throws IOException {
        return PersistentCharSequenceEnumerator.super.enumerate(value);
      }

      @Override
      public @Nullable CharSequence valueOf(int idx) throws IOException {
        return PersistentCharSequenceEnumerator.super.valueOf(idx);
      }
    }, EnumeratorCharSequenceDescriptor.INSTANCE) : null;
  }

  @Override
  public int enumerate(@Nullable CharSequence value) throws IOException {
    return myCache != null ? myCache.enumerate(value) : super.enumerate(value);
  }

  @Override
  public @Nullable CharSequence valueOf(int idx) throws IOException {
    return myCache != null ? myCache.valueOf(idx) : super.valueOf(idx);
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (myCache != null) myCache.close();
  }
}

