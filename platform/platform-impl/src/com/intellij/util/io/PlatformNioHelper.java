// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.function.BiPredicate;

@ApiStatus.Internal
public final class PlatformNioHelper {
  private PlatformNioHelper() { }

  public static final class Result<V> {
    private final Object value;

    private Result(Object value) {
      this.value = value;
    }

    @SuppressWarnings("unchecked")
    public V get() throws IOException {
      if (value instanceof RuntimeException) throw (RuntimeException)value;
      if (value instanceof IOException) throw (IOException)value;
      return (V)value;
    }
  }

  /**
   * A specialized alternative to {@link Files#newDirectoryStream} and {@link Files#walkFileTree}.
   *
   * @see NioFiles#readAttributes
   */
  public static void visitDirectory(
    @NotNull Path directory,
    @Nullable Set<String> filter,
    @NotNull BiPredicate<Path, Result<BasicFileAttributes>> consumer
  ) throws IOException, SecurityException {
    try (var dirStream = Files.newDirectoryStream(directory)) { // Use standard API here
      for (var path : dirStream) {
        if (filter != null && !filter.contains(path.getFileName().toString())) {
          continue;
        }

        Result<BasicFileAttributes> result;
        try {
          // Use the standard `Files.readAttributes` to obtain attributes
          BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
          result = new Result<>(attrs);
        } catch (IOException | RuntimeException e) { // Handle errors appropriately
          result = new Result<>(e);
        }

        if (!consumer.test(path, result)) {
          break;
        }
      }
    }
  }
}
