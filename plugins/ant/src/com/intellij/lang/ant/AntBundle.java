package com.intellij.lang.ant;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

public class AntBundle {
  @NonNls private static final String BUNDLE = "com.intellij.lang.ant.resources.AntBundle";
  private static Reference<ResourceBundle> ourBundle;

  private AntBundle() {
  }

  @Nullable
  public static String getMessage(@NonNls @PropertyKey(resourceBundle = BUNDLE) final String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }
  
  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
