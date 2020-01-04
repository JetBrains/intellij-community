// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import static com.intellij.BundleUtil.loadLanguageBundle;

public class UIBundle {
  private static final String BUNDLE = "messages.UIBundle";
  private static ResourceBundle ourBundle;

  @Nls
  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private UIBundle() { }

  private static ResourceBundle getBundle() {
    if (ourBundle != null) return ourBundle;
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }

  public static void loadBundleFromPlugin(@Nullable ClassLoader pluginClassLoader) {
    ResourceBundle bundle = loadLanguageBundle(pluginClassLoader, BUNDLE);
    if (bundle != null) ourBundle = bundle;
  }
}