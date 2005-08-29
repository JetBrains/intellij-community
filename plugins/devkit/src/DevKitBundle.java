package org.jetbrains.idea.devkit;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

/**
 * User: anna
 * Date: Aug 11, 2005
 */
public class DevKitBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("org.jetbrains.idea.devkit.DevKitBundle");

  private  DevKitBundle(){}

  public static String message(@NonNls String key, Object... params) {
    String value;
    try {
      value = ourBundle.getString(key);
    }
    catch (MissingResourceException e) {
      return "!" + key + "!";
    }

    if (params.length > 0) {
      return MessageFormat.format(value, params);
    }

    return value;
  }
}
