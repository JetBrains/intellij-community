package com.intellij.lang.ant;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public class AntBundle {

  private static ResourceBundle theBundle = null;

  private AntBundle() {
  }

  @Nullable
  public static String getMessage(@NonNls final String key, Object... params) {
    if (theBundle == null) {
      theBundle = ResourceBundle.getBundle("com.intellij.lang.ant.resources.AntBundle");
    }
    return CommonBundle.message(theBundle, key, params);
  }
}
