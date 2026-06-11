// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.impl.local.windows.WindowsBufferedDirectoryStream;
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2.FetchAttributesFilter;
import com.intellij.util.system.OS;
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

  /// A specialized alternative to [Files#newDirectoryStream] and [Files#walkFileTree].
  /// Only children whose names are in the filter set are passed to consumer.
  /// `(filter == null)` means 'no filter', i.e., all children must be passed to consumer; `filter={}` (an empty set) means 'do nothing'.
  ///
  /// @see NioFiles#readAttributes
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void visitDirectory(
    @NotNull Path directory,
    @Nullable Set<String> filter,
    @NotNull BiPredicate<Path, Result<BasicFileAttributes>> consumer
  ) throws IOException, SecurityException {
    if (filter != null && filter.isEmpty()) return;  // nothing to read

    if (OS.CURRENT == OS.Windows && Registry.is("vfs.windows.use.buffered.directory.stream", true)) {
      try (final var dirStream = new WindowsBufferedDirectoryStream(directory)) {
        for (final var pathAttrs : dirStream) {
          final var path = pathAttrs.getFirst();
          final var attrs = pathAttrs.getSecond();

          if (!consumer.test(path, new Result<>(attrs))) {
            break;
          }
        }
      }
      return;
    }

    try (var dirStream = directory.getFileSystem().provider().newDirectoryStream(directory, FetchAttributesFilter.ACCEPT_ALL)) {
      for (var path : dirStream) {
        if (filter != null && !filter.contains(path.getFileName().toString())) {
          continue;
        }

        var attrs = path instanceof sun.nio.fs.BasicFileAttributesHolder holder ? holder.get() : null;

        Result<BasicFileAttributes> result;
        if (attrs != null) {
          result = new Result<>(attrs);
        }
        else {
          try {
            result = new Result<>(NioFiles.readAttributes(path));
          }
          catch (IOException | RuntimeException e) {
            result = new Result<>(e);
          }
        }

        if (!consumer.test(path, result)) {
          break;
        }
      }
    }
  }
}
