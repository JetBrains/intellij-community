// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.settings;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

public class EditorConfigBundle {

  public static String message(@NotNull @PropertyKey(resourceBundle = EDITOR_CONFIG_BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static Reference<ResourceBundle> ourBundle;
  @NonNls public static final String EDITOR_CONFIG_BUNDLE = "messages.EditorConfigBundle";

  private EditorConfigBundle() {
  }

  @SuppressWarnings("Duplicates")
  private static ResourceBundle getBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(EDITOR_CONFIG_BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }
}
