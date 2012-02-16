package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.ui.GradleIcons;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:18 AM
 */
public enum GradleEntityType {
  MODULE(GradleIcons.MODULE_ICON), MODULE_DEPENDENCY(GradleIcons.MODULE_ICON), LIBRARY_DEPENDENCY(GradleIcons.LIB_ICON), SYNTHETIC(null),
  PROJECT(null) {
    @Nullable
    @Override
    public Icon getIcon() {
      return ServiceManager.getService(PlatformFacade.class).getProjectIcon();
    }
  };

  @Nullable private final Icon myIcon;

  GradleEntityType(@Nullable Icon icon) {
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }
}
