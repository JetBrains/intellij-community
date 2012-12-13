package org.jetbrains.plugins.gradle.model;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.ui.GradleIcons;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:18 AM
 */
public enum GradleEntityType {
  PROJECT(GradleIcons.PROJECT_ICON), MODULE(AllIcons.Nodes.Module), MODULE_DEPENDENCY(AllIcons.Nodes.Module),
  LIBRARY(AllIcons.Nodes.PpLib), LIBRARY_DEPENDENCY(AllIcons.Nodes.PpLib), CONTENT_ROOT(null), SYNTHETIC(null),
  JAR(AllIcons.FileTypes.Archive);

  @Nullable private final Icon myIcon;

  GradleEntityType(@Nullable Icon icon) {
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }
}
