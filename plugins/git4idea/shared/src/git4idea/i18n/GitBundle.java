// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.i18n;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class GitBundle {
  public static final @NonNls String BUNDLE = "messages.GitBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(GitBundle.class, BUNDLE);

  private GitBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  /**
   * @deprecated prefer {@link #message(String, Object...)} instead
   */
  @Deprecated
  public static @NotNull @Nls String getString(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return message(key);
  }
}