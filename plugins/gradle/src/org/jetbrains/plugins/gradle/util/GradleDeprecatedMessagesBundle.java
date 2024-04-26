package org.jetbrains.plugins.gradle.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class GradleDeprecatedMessagesBundle extends DynamicBundle {
  @NonNls public static final String PATH_TO_BUNDLE = "messages.GradleDeprecatedMessagesBundle";
  private static final GradleDeprecatedMessagesBundle BUNDLE = new GradleDeprecatedMessagesBundle();

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getLazyMessage(key, params);
  }

  public GradleDeprecatedMessagesBundle() {
    super(PATH_TO_BUNDLE);
  }
}
