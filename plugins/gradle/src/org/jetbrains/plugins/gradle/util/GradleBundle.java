package org.jetbrains.plugins.gradle.util;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Denis Zhdanov
 * @since 8/1/11 2:44 PM
 */
public class GradleBundle extends AbstractBundle {

  public static final String PATH_TO_BUNDLE = "i18n.GradleBundle";
  
  private static final GradleBundle BUNDLE = new GradleBundle();
  
  public GradleBundle() {
    super(PATH_TO_BUNDLE);
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object... params) {
    return BUNDLE.getMessage(key, params);
  }
}
