package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleIcons {
  
  public static final Icon GRADLE_ICON = IconLoader.getIcon("/icons/gradle.png");

  private GradleIcons() {
  }
}
