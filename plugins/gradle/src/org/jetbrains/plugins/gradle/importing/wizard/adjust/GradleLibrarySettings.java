package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 4:22 PM
 */
public class GradleLibrarySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibraryDependency myLibraryDependency;
  private final JComponent              myComponent;
  private final JLabel                  myNameErrorLabel;
  
  public GradleLibrarySettings(@NotNull GradleLibraryDependency libraryDependency) {
    myLibraryDependency = libraryDependency;
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameErrorLabel = GradleAdjustImportSettingsUtil.configureNameControl(builder, libraryDependency);
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return GradleAdjustImportSettingsUtil.validate(myLibraryDependency, myNameErrorLabel);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
