package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.Gray;

import java.awt.*;

/**
 * Holds keys to the colors used at gradle-specific processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/18/12 4:19 PM
 */
public class GradleColorKeys {
  
  /**
   * References color to use for indication of particular change that exists only at the gradle side.
   * <p/>
   * Example: particular dependency is added at the gradle side but not at intellij.
   */
  public static final ColorKey GRADLE_LOCAL_CHANGE = ColorKey.createColorKey("GRADLE_LOCAL_CHANGE", new Color(130, 184, 22));
  public static final ColorKey GRADLE_INTELLIJ_LOCAL_CHANGE = ColorKey.createColorKey("GRADLE_INTELLIJ_LOCAL_CHANGE", new Color(16, 102, 248));
  public static final ColorKey GRADLE_CHANGE_CONFLICT = ColorKey.createColorKey("GRADLE_CHANGE_CONFLICT", new Color(255, 187, 255));
  public static final ColorKey GRADLE_CONFIRMED_CONFLICT = ColorKey.createColorKey("GRADLE_CONFIRMED_CONFLICT", Gray._208);
  
  private GradleColorKeys() {
  }
}
