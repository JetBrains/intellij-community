// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void visitDirectory(@NotNull Path directory, @NotNull BiPredicate<Path, Result<BasicFileAttributes>> consumer)
  throws IOException, SecurityException {
    try (var dirStream = Files.newDirectoryStream(directory)) {
      for (var path : dirStream) {
        BasicFileAttributes attrs = null;
        if (path instanceof sun.nio.fs.BasicFileAttributesHolder) {
          attrs = ((sun.nio.fs.BasicFileAttributesHolder)path).get();
        }

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
