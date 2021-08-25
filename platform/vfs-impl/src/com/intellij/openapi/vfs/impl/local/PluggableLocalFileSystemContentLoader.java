// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
public interface PluggableLocalFileSystemContentLoader extends Disposable {
  void initialize();

  @Nullable InputStream getInputStream(@NotNull File absoluteFile) throws NoSuchFileException;

  byte @Nullable [] contentToByteArray(@NotNull File absoluteFile) throws NoSuchFileException;
}
