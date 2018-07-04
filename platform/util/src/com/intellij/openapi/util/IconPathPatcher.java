// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconPathPatcher {
  /**
   * @deprecated
   * @see #patchPath(String, Class)
   */
  @Deprecated
  @Nullable
  public String patchPath(String path) {
    return patchPath(path, null);
  }

  /**
   * Patches the path or returns null if nothing has patched
   * @param path path to the icon
   * @param classLoader ClassLoader of the icon is requested from
   * @return patched path or null
   */
  @Nullable
  public String patchPath(String path, ClassLoader classLoader) {
    return null;
  }

  /**
   * @deprecated
   * @see #getContextClass(String, Class)
   */
  @Deprecated
  public Class getContextClass(String path) {
    return null;
  }

  /**
   * Return ClassLoader for icon path or returns null if nothing has patched
   * @param path path to the icon
   * @param originalClassLoader ClassLoader of the icon is requested from
   * @return patched icon ClassLoader or null
   */
  @Nullable
  public ClassLoader getContextClassLoader(String path, ClassLoader originalClassLoader) {
    return null;
  }
}
