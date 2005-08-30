package org.jetbrains.idea.devkit;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * User: anna
 * Date: Aug 11, 2005
 */
public class DevKitBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("org.jetbrains.idea.devkit.DevKitBundle");

  private  DevKitBundle(){}

  public static String message(@PropertyKey String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
