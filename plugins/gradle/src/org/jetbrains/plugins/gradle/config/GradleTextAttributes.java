package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
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
public class GradleTextAttributes {
  
  /**
   * References color to use for indication of particular change that exists only at the gradle side.
   * <p/>
   * Example: particular dependency is added at the gradle side but not at the intellij.
   */
  public static final TextAttributesKey GRADLE_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "GRADLE_LOCAL_CHANGE",
    new TextAttributes(new Color(130, 184, 22), null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication of particular change that exists only at the intellij side.
   * <p/>
   * Example: particular dependency is added at the intellij side but not at the gradle.
   */
  public static final TextAttributesKey GRADLE_INTELLIJ_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "GRADLE_INTELLIJ_LOCAL_CHANGE",
    new TextAttributes(new Color(16, 102, 248), null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication that particular setting has different values at the gradle and intellij.
   * <p/>
   * Example: particular module is renamed at the intellij, i.e. <code>'module.name'</code> property has different (conflicting)
   * values at the gradle and the intellij.
   */
  public static final TextAttributesKey GRADLE_CHANGE_CONFLICT = TextAttributesKey.createTextAttributesKey(
    "GRADLE_CHANGE_CONFLICT",
    new TextAttributes(new Color(238, 0, 0), null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication that particular setting has the same values at the gradle and intellij.
   */
  public static final TextAttributesKey GRADLE_NO_CHANGE = TextAttributesKey.createTextAttributesKey(
    "GRADLE_NO_CHANGE",
    new TextAttributes(Color.BLACK, null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication that particular change should be ignored during the gradle and intellij project structures
   * comparison.
   * <p/>
   * Example: particular dummy module specific to the local environment is added at the intellij side but we don't want to propagate
   * that to the gradle side and don't want to see it during the project structures comparison.
   */
  public static final TextAttributesKey GRADLE_CONFIRMED_CONFLICT = TextAttributesKey.createTextAttributesKey(
    "GRADLE_CONFIRMED_CONFLICT",
    new TextAttributes(Gray._140, null, null, null, Font.PLAIN)
  );
  
  private GradleTextAttributes() {
  }
}
