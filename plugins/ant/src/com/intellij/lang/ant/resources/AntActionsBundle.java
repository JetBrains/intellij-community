package com.intellij.lang.ant.resources;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

public final class AntActionsBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls private static final String IDEA_ACTIONS_BUNDLE = "com.intellij.lang.ant.resources.AntActionsBundle";

  private AntActionsBundle() {
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionText(@NonNls String actionId) {
    return message("action." + actionId + ".text");
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionDescription(@NonNls String actionId) {
    return message("action." + actionId + ".description");
  }

  public static String message(@PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(IDEA_ACTIONS_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}