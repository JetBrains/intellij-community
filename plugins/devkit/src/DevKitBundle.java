package org.jetbrains.idea.devkit;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * User: anna
 * Date: Aug 11, 2005
 */
public class DevKitBundle {
  @NonNls private static final String BUNDLE = "org.jetbrains.idea.devkit.DevKitBundle";

  private  DevKitBundle(){}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
