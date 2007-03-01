package org.jetbrains.plugins.groovy;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import com.intellij.CommonBundle;

/**
 * @author Ilya.Sergey
 */
public class GroovyBundle {

  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  private static final String BUNDLE = "org.jetbrains.plugins.groovy.GroovyBundle";

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
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
