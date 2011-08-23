package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleModule;
import org.jetbrains.plugins.gradle.util.GradleUiUtil;

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

  public GradleModuleSettings(@NotNull GradleModule module) {
    myModule = module;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    GradleUiUtil.configureNameControl(builder, myModule);
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
