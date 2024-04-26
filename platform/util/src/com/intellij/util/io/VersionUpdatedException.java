// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

//TODO RC: extending CorruptedException is a bit off for VersionUpdatedException -- storage is not corrupted,
//         it is just different version. Probably, at some point 'corruptions' and 'storage version bumps' were
//         processed in a same way, hence extending CorruptedException was an easy win. But it is logically
//         incorrect, and led to a bug (luckily, only in statistics this time)
public final class VersionUpdatedException extends CorruptedException {
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
