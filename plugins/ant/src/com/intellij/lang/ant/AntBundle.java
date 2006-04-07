package com.intellij.lang.ant;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public class AntBundle {

  private static ResourceBundle theBundle = null;

  private AntBundle() {
  }

  @Nullable
  public static String getMessage(@NonNls final String key) {
    if (theBundle == null) {
      theBundle = ResourceBundle.getBundle("com.intellij.lang.ant.AntBundle");
    }
    return theBundle.getString(key);
  }
}
