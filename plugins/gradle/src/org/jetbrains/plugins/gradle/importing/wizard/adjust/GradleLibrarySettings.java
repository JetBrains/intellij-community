package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleLibrary;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 4:22 PM
 */
public class GradleLibrarySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibrary myLibrary;
  private final JComponent    myComponent;
  private final JLabel        myNameErrorLabel;
  
  public GradleLibrarySettings(@NotNull GradleLibrary library) {
    myLibrary = library;
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameErrorLabel = GradleAdjustImportSettingsUtil.configureNameControl(builder, library);
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return GradleAdjustImportSettingsUtil.validate(myLibrary, myNameErrorLabel);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
