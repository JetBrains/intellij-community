// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Open storage A over given file.
 * The idea is to separate configuration and opening of the storage: all storage-specific parameters (like
 * readOnly, pageSize, how to recover from corruptions, how to check storage version and what to do with older
 * versions ...) are wrapped into a factory, and configured factory is given to another part of the system
 * which just opens the storage over given file.
 */
@FunctionalInterface
@ApiStatus.Internal
public interface StorageFactory<A extends AutoCloseable> {
  @NotNull A open(@NotNull Path storagePath) throws IOException;

  /**
   * Opens the storage, and passes it to function for wrapping another storage around it.
   * If the wrapping function throws exception -- method closes the storage just opened before propagating
   * exception up the stack.
   */
  default <S, E extends Throwable> @NotNull S wrapStorageSafely(@NotNull Path storagePath,
                                                                @NotNull ThrowableNotNullFunction<? super A, ? extends S, E> anotherStorageOpener)
    throws IOException, E {
    return IOUtil.wrapSafely(
      open(storagePath),
      anotherStorageOpener::fun
    );
  }

  /** Just a function composition: Factory[Path->A] (x) Opener[A->B] = Factory[Path->B] */
  default <B extends AutoCloseable> @NotNull StorageFactory<B> compose(
    @NotNull ThrowableNotNullFunction<? super A, ? extends B, ? extends IOException> anotherStorageOpener
  ) {
    return storagePath -> wrapStorageSafely(storagePath, anotherStorageOpener);
  }
}
