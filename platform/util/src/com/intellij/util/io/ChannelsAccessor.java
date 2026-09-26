// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/** Abstracts different ways of caching/not caching opened {@linkplain FileChannel}s */
@ApiStatus.Internal
public interface ChannelsAccessor {
  boolean isReadOnly();

  <T> T executeOp(@NotNull Path path,
                  @NotNull FileChannelOperation<T> operation) throws IOException;

  <T> T executeIdempotentOp(@NotNull Path path,
                            @NotNull FileChannelIdempotentOperation<T> operation) throws IOException;

  void closeChannel(@NotNull Path path) throws IOException;

  @FunctionalInterface
  interface FileChannelOperation<T> {
    T execute(@NotNull FileChannel channel) throws IOException;
  }

  @FunctionalInterface
  interface FileChannelOpener {
    FileChannel open(@NotNull Path path, boolean readOnly) throws IOException;
  }
}

/** Optional diagnostics for cache-backed accessors, used by storage lifecycle checks. */
interface DiagnosticChannelsAccessor {
  /** Describes a cached channel for the path, or returns {@code null} if this accessor has no such channel open. */
  @Nullable String describeCachedChannelOrNull(@NotNull Path path);
}
