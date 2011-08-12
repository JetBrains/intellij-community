package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleModule;

import javax.swing.*;

/**
 * Manages settings of {@link GradleModule gradle module} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 3:39 PM
 */
public class GradleModuleSettings implements GradleProjectStructureNodeSettings {

  private final JTextField myNameTextField = new JTextField();

  private final JComponent   myComponent;
  private final GradleModule myModule;

  public GradleModuleSettings(@NotNull GradleModule module) {
    myModule = module;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();

    builder.add("gradle.import.structure.settings.label.name", myNameTextField);
    myNameTextField.setText(module.getName());

    myComponent = builder.build();
  }

  @Override
  public boolean commit() {
    // TODO den implement 
    return true;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
