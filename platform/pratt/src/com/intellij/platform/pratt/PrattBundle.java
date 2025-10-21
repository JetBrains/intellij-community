// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pratt;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

@ApiStatus.Internal
public final class PrattBundle {
  public static final @NonNls String BUNDLE = "messages.PrattBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(PrattBundle.class, BUNDLE);

  private PrattBundle() { }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}