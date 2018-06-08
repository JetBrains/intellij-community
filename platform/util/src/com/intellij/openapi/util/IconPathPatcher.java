// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconPathPatcher {
  /**
   * Patches the path or returns null if nothing has patched
   * @param path path to the icon
   * @return patched path or null
   */
  @Nullable
  public abstract String patchPath(String path);

  @Nullable
  public Class getContextClass(String path) {
    return null;
  }
}
