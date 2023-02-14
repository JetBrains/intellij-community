// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@ApiStatus.Internal
public interface ZipFile extends AutoCloseable {
  @Nullable InputStream getInputStream(@NotNull String path) throws IOException;

  @Nullable ByteBuffer getByteBuffer(@NotNull String path) throws IOException;

  byte @Nullable [] getData(String name) throws IOException;

  @Nullable ZipResource getResource(String name);

  void processResources(@NotNull String dir,
                        @NotNull Predicate<? super String> nameFilter,
                        @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException;

  default void releaseBuffer(ByteBuffer buffer) {
    if (!buffer.isReadOnly()) {
      DirectByteBufferPool.DEFAULT_POOL.release(buffer);
    }
  }

  interface ZipResource {
    int getUncompressedSize();

    @NotNull String getPath();

    @NotNull ByteBuffer getByteBuffer() throws IOException;

    byte @NotNull [] getData() throws IOException;

    @NotNull InputStream getInputStream() throws IOException;
  }
}
