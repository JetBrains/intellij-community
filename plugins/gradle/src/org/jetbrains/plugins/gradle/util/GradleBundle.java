// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class GradleBundle extends DynamicBundle {
  public static final @NonNls String PATH_TO_BUNDLE = "messages.GradleBundle";
  private static final GradleBundle BUNDLE = new GradleBundle();

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.containsKey(key) ? BUNDLE.getMessage(key, params) : GradleDeprecatedMessagesBundle.message(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.containsKey(key) ? BUNDLE.getLazyMessage(key, params) : GradleDeprecatedMessagesBundle.messagePointer(key, params);
  }

  public GradleBundle() {
    super(PATH_TO_BUNDLE);
  }
}
