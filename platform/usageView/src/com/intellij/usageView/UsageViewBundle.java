// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeDeprecatedMessagesBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class UsageViewBundle {
  private static final @NonNls String BUNDLE = "messages.UsageViewBundle";

  private static final DynamicBundle INSTANCE = new DynamicBundle(UsageViewBundle.class, BUNDLE);

  private UsageViewBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.message(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getLazyMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.messagePointer(key, params);
  }

  @SuppressWarnings({"AutoBoxing"})
  public static String getOccurencesString(int usagesCount, int filesCount) {
    return " (" + message("occurence.info.occurence", usagesCount, filesCount) + ")";
  }

  @SuppressWarnings({"AutoBoxing"})
  public static @Nls String getReferencesString(int usagesCount, int filesCount) {
    return " (" + message("occurence.info.reference", usagesCount, filesCount) + ")";
  }
}