package com.intellij.configurationScript;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

final class ConfigurationScriptBundle extends DynamicBundle {
  private static final @NonNls String BUNDLE = "messages.ConfigurationScriptBundle";
  private static final ConfigurationScriptBundle INSTANCE = new ConfigurationScriptBundle();

  private ConfigurationScriptBundle() {
    super(BUNDLE);
  }

  public static @Nls @NotNull String message(
    @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    @NotNull Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(
    @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    @NotNull Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
