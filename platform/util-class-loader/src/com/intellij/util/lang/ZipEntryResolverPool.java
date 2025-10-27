// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link #close()} closes all loaded zip files
 */
@ApiStatus.Internal
public interface ZipEntryResolverPool extends Closeable {
  @NotNull EntryResolver load(@NotNull Path file) throws IOException;

  /**
   * {@link #close()} is a hint that the instance is no longer in use
   */
  interface EntryResolver extends Closeable {
    byte @Nullable [] loadZipEntry(@NotNull String path) throws IOException;
  }
}
