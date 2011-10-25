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

  private final JComponent   myComponent;
  private final GradleModule myModule;
  private final JTextField   myNameControl;

  public GradleModuleSettings(@NotNull GradleModule module) {
    myModule = module;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameControl = GradleAdjustImportSettingsUtil.configureNameControl(builder, myModule);
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return GradleAdjustImportSettingsUtil.validate(myModule, myNameControl);
  }

  @Override
  public void refresh() {
    myNameControl.setText(myModule.getName());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
