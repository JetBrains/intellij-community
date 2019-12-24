package com.intellij.structuralsearch;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class SSRBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.SSRBundle";
  private static final SSRBundle INSTANCE = new SSRBundle();

  private SSRBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}