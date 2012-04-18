package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.ui.GradleIcons;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:18 AM
 */
public enum GradleEntityType {
  PROJECT(GradleIcons.PROJECT_ICON), MODULE(GradleIcons.MODULE_ICON), MODULE_DEPENDENCY(GradleIcons.MODULE_ICON),
  LIBRARY(GradleIcons.LIB_ICON), LIBRARY_DEPENDENCY(GradleIcons.LIB_ICON), CONTENT_ROOT(GradleIcons.CONTENT_ROOT_ICON), SYNTHETIC(null);

  @Nullable private final Icon myIcon;

  GradleEntityType(@Nullable Icon icon) {
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }
}
