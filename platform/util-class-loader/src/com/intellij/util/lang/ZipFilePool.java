// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

// API and placement of classes are complicated due to java 8 requirement
@ApiStatus.Internal
public abstract class ZipFilePool {
  @SuppressWarnings("StaticNonFinalField")
  public static ZipFilePool POOL;

  public abstract @NotNull EntryResolver load(@NotNull Path file) throws IOException;

  public abstract @NotNull Object loadZipFile(@NotNull Path file) throws IOException;

  public interface EntryResolver {
    @Nullable InputStream loadZipEntry(@NotNull String path) throws IOException;
  }
}
