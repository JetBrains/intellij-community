// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public final class EmptyZipFile implements ZipFile {
  @Override
  public @Nullable InputStream getInputStream(@NotNull String path) throws IOException {
    return null;
  }

  @Override
  public @Nullable ByteBuffer getByteBuffer(@NotNull String path) throws IOException {
    return null;
  }

  @Override
  public byte @Nullable [] getData(String name) throws IOException {
    return null;
  }

  @Override
  public @Nullable ZipResource getResource(String name) {
    return null;
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> nameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) {
  }

  @Override
  public void close() throws Exception {
  }
}
