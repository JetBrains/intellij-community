// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

/// A bridge used by [Compressor] and [Decompressor] to access the Eel API.
@ApiStatus.Internal
public abstract class ArchiveBackend {
  public abstract boolean isWindows(@NotNull Path path);

  static boolean isOnWindows(@NotNull Path path) {
    Iterator<ArchiveBackend> backends = ServiceLoader.load(ArchiveBackend.class).iterator();
    return backends.hasNext() ? backends.next().isWindows(path) : OS.CURRENT == OS.Windows;
  }
}
