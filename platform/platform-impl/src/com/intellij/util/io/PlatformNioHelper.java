// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;

@ApiStatus.Internal
public final class PlatformNioHelper {
  private static final Logger LOG = Logger.getInstance(PlatformNioHelper.class);

  private PlatformNioHelper() { }

  /**
   * A specialized alternative to {@link Files#newDirectoryStream} and {@link Files#walkFileTree}.
   *
   * @see NioFiles#readAttributes
   */
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void visitDirectory(@NotNull Path directory, @NotNull BiConsumer<Path, @Nullable BasicFileAttributes> consumer) {
    try (var dirStream = Files.newDirectoryStream(directory)) {
      for (var path : dirStream) {
        BasicFileAttributes attrs = null;
        if (path instanceof sun.nio.fs.BasicFileAttributesHolder) {
          attrs = ((sun.nio.fs.BasicFileAttributesHolder)path).get();
        }
        if (attrs == null) {
          try {
            attrs = NioFiles.readAttributes(path);
          }
          catch (IOException | SecurityException e) {
            LOG.debug(e);
          }
        }
        consumer.accept(path, attrs);
      }
    }
    catch (IOException | SecurityException e) {
      LOG.debug(e);
    }
  }
}
