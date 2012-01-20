package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 1/18/12 1:16 PM
 */
public class GradleIcons {

  public static final Icon GRADLE_ICON       = IconLoader.getIcon("/icons/gradle.png");
  public static final Icon LIB_ICON          = IconLoader.getIcon("/nodes/ppLib.png");
  public static final Icon PROJECT_ICON      = IconLoader.getIcon("/nodes/ideaProject.png");
  public static final Icon MODULE_ICON       = IconLoader.getIcon("/nodes/ModuleOpen.png");
  public static final Icon CONTENT_ROOT_ICON = IconLoader.getIcon("/modules/addContentEntry.png");

  private GradleIcons() {
  }
}
