package org.jetbrains.android.util;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * Messages bundle.
 *
 * @author Alexey Efimov
 */
public final class AndroidBundle {
  @NonNls
  private static final String BUNDLE_NAME = "messages.AndroidBundle";
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

  private AndroidBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }
}
