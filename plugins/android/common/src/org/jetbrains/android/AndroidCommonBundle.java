package org.jetbrains.android;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonBundle {
  @NonNls private static final String BUNDLE_NAME = "messages.AndroidCommonBundle";
  private static Reference<ResourceBundle> ourBundle;

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE_NAME);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }

  private AndroidCommonBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }
}
