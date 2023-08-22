// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@ApiStatus.Internal
public interface PluggableLocalFileSystemContentLoader extends Disposable {
  void initialize();

  @Nullable InputStream getInputStream(@NotNull Path absolutePath) throws NoSuchFileException;

  byte @Nullable [] contentToByteArray(@NotNull Path absolutePath) throws NoSuchFileException;
}
