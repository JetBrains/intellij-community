// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class RootFileValidityChecker {
  private static final Logger LOG = Logger.getInstance(RootFileValidityChecker.class);

  public static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!file.isValid()) {
      if (containerProvider != null) {
        Class<?> providerClass = containerProvider.getClass();
        PluginException.logPluginError(LOG, "Invalid root " + file + " in " + container + " provided by " + providerClass, null, providerClass);
      }
      else {
        LOG.error("Invalid root " + file + " in " + container);
      }
      return false;
    }
    return true;
  }

  public static @Nullable VirtualFile correctRoot(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!ensureValid(file, container, containerProvider)) {
      return null;
    }
    return file;
  }
}
