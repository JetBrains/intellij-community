// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2.FetchAttributesFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
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
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void visitDirectory(
    @NotNull Path directory,
    @Nullable Set<String> filter,
    @NotNull BiPredicate<Path, Result<BasicFileAttributes>> consumer
  ) throws IOException, SecurityException {
    try (var dirStream = directory.getFileSystem().provider().newDirectoryStream(directory, getRelevantAllAcceptingPathFilter(directory))) {
      for (var path : dirStream) {
        if (filter != null && !filter.contains(path.getFileName().toString())) {
          continue;
        }

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

  /**
   * In production, we have two coexisting app class loaders: {@link jdk.internal.loader.ClassLoaders.AppClassLoader} and {@link com.intellij.util.lang.PathClassLoader}.
   * The code executing here is running under {@link com.intellij.util.lang.PathClassLoader}, but the classes of routing FS are loaded with {@link jdk.internal.loader.ClassLoaders.AppClassLoader}.
   * These two class loaders are not related to each other, hence checks for {@code instanceof} between objects loaded with them will fail.
   * Here we forcefully retrieve a filter belonging to the necessary classloader with the sole purpose of having matching loaders.
   */
  private static @NotNull DirectoryStream.Filter<Path> getRelevantAllAcceptingPathFilter(@NotNull Path directory) {
    try {
      ClassLoader classLoader = directory.getClass().getClassLoader();
      if (classLoader == null) {
        return FetchAttributesFilter.ACCEPT_ALL;
      }
      //noinspection unchecked
      return (DirectoryStream.Filter<Path>)classLoader.loadClass(
        "com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2$FetchAttributesFilter").getField("ACCEPT_ALL").get(null);
    }
    catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
      return FetchAttributesFilter.ACCEPT_ALL;
    }
  }
}
