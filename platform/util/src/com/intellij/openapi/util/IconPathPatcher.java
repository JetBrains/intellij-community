// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconPathPatcher {
  /**
   * @deprecated use {@link #patchPath(String, ClassLoader)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public @Nullable String patchPath(@NotNull String path) {
    return patchPath(path, null);
  }

  /**
   * Patches the path or returns {@code null} if nothing has patched.
   *
   * @param path        path to the icon
   * @param classLoader ClassLoader of the icon is requested from
   * @return patched path or {@code null}
   */
  public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
    return null;
  }

  /**
   * @deprecated use {@link #getContextClassLoader(String, ClassLoader)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public Class<?> getContextClass(@NotNull String path) {
    return null;
  }

  /**
   * Return ClassLoader for icon path or {@code null} if nothing has patched.
   *
   * @param path                path to the icon
   * @param originalClassLoader ClassLoader of the icon is requested from
   * @return patched icon ClassLoader or {@code null}
   */
  public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
    return null;
  }
}
