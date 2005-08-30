package org.jetbrains.idea.devkit;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

import com.intellij.CommonBundle;

/**
 * User: anna
 * Date: Aug 11, 2005
 */
public class DevKitBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("org.jetbrains.idea.devkit.DevKitBundle");

  private  DevKitBundle(){}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
