package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * Holds keys to the colors used at gradle-specific processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/18/12 4:19 PM
 */
// TODO den introduce fallback text attribute keys when v.12.1 is released.
public class GradleTextAttributes {

  /**
   * References color to use for indication of particular change that exists only at the gradle side.
   * <p/>
   * Example: particular dependency is added at the gradle side but not at the intellij.
   */
  public static final TextAttributesKey GRADLE_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "GRADLE_LOCAL_CHANGE",
    new TextAttributes(JBColor.GREEN, null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication of particular change that exists only at the intellij side.
   * <p/>
   * Example: particular dependency is added at the intellij side but not at the gradle.
   */
  public static final TextAttributesKey INTELLIJ_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "INTELLIJ_LOCAL_CHANGE",
    new TextAttributes(JBColor.BLUE, null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication that particular setting has different values at the gradle and intellij.
   * <p/>
   * Example: particular module is renamed at the intellij, i.e. <code>'module.name'</code> property has different (conflicting)
   * values at the gradle and the intellij.
   */
  public static final TextAttributesKey CHANGE_CONFLICT = TextAttributesKey.createTextAttributesKey(
    "GRADLE_CHANGE_CONFLICT",
    new TextAttributes(JBColor.RED, null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey OUTDATED_ENTITY = TextAttributesKey.createTextAttributesKey(
    "GRADLE_OUTDATED_ENTITY",
    new TextAttributes(JBColor.ORANGE, null, null, null, Font.PLAIN)
  );

  /**
   * References color to use for indication that particular setting has the same values at the gradle and intellij.
   */
  public static final TextAttributesKey NO_CHANGE = TextAttributesKey.createTextAttributesKey(
    "GRADLE_NO_CHANGE",
    new TextAttributes(JBColor.BLACK, null, null, null, Font.PLAIN)
  );

  private GradleTextAttributes() {
  }
}
