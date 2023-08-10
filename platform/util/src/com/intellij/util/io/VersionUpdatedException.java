// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class VersionUpdatedException extends CorruptedException {
  @ApiStatus.Internal
  public VersionUpdatedException(@NotNull Path file) {
    super("Storage version updated, file = " + file);
  }

  @ApiStatus.Internal
  public VersionUpdatedException(@NotNull Path file, @NotNull Object expectedVersion, @NotNull Object actualVersion) {
    super("Storage version updated" +
          ", file = " + file +
          ", expected version = " + expectedVersion +
          ", actual version = " + actualVersion);
  }
}
