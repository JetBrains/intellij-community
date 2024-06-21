// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.BundleBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;


@ApiStatus.Internal
public final class UtilUiBundle {
  private static final String BUNDLE = "messages.UtilUiBundle";
  private static ResourceBundle ourBundle;

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(getBundle(), key, null, params);
  }

  private UtilUiBundle() { }

  private static ResourceBundle getBundle() {
    if (ourBundle != null) return ourBundle;
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }
}