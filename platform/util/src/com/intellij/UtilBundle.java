// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import static com.intellij.BundleUtil.loadLanguageBundle;

public final class UtilBundle extends BundleBase {
  private static final String BUNDLE = "messages.UtilBundle";
  private static ResourceBundle ourBundle;

  private UtilBundle() { }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return message(getUtilBundle(), key, params);
  }

  @NotNull
  private static ResourceBundle getUtilBundle() {
    if (ourBundle != null) return ourBundle;
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }

  public static void loadBundleFromPlugin(@Nullable ClassLoader pluginClassLoader) {
    ResourceBundle bundle = loadLanguageBundle(pluginClassLoader, BUNDLE);
    if (bundle != null) ourBundle = bundle;
  }
}