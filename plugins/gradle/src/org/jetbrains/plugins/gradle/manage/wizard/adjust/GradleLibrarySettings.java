package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 4:22 PM
 */
public class GradleLibrarySettings implements GradleProjectStructureNodeSettings {

  private final JComponent myComponent;

  public GradleLibrarySettings() {
    myComponent = new JPanel();
  }

  @Override
  public boolean validate() {
    return true;
  }

  @Override
  public void refresh() {
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
