// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Not localized - use {@link com.intellij.util.UtilBundle}
 */
@ApiStatus.Obsolete
@ApiStatus.Internal
public final class UtilBundle {
  private static final String BUNDLE = "messages.UtilBundle";
  private static final AbstractBundle bundle = new AbstractBundle(UtilBundle.class, BUNDLE);

  private UtilBundle() { }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return bundle.getMessage(key, params);
  }
}