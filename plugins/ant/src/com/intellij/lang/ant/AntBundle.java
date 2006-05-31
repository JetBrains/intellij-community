package com.intellij.lang.ant;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class AntBundle {
  @NonNls private static final String BUNDLE = "com.intellij.lang.ant.resources.AntBundle";

  private AntBundle() {
  }

  @Nullable
  public static String getMessage(@NonNls @PropertyKey(resourceBundle = BUNDLE) final String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
