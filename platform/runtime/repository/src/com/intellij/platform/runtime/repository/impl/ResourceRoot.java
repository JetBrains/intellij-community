// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

abstract class ResourceRoot {
  public abstract @Nullable InputStream openFile(@NotNull String relativePath) throws IOException;

  public abstract @NotNull Path getRootPath();
}
