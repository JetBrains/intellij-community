// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public final class UtilBundle {
  private static final String BUNDLE = "messages.UtilBundle";
  private static ResourceBundle ourBundle;

  private UtilBundle() { }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return BundleBaseKt.messageOrDefault(getUtilBundle(), key, null, params);
  }

  private static @NotNull ResourceBundle getUtilBundle() {
    if (ourBundle != null) {
      return ourBundle;
    }
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }
}