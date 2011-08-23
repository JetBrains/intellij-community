package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.util.GradleUiUtil;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 4:22 PM
 */
public class GradleLibrarySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibraryDependency myLibraryDependency;
  private final JComponent              myComponent;
  
  public GradleLibrarySettings(@NotNull GradleLibraryDependency libraryDependency) {
    myLibraryDependency = libraryDependency;
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    GradleUiUtil.configureNameControl(builder, libraryDependency);
    myComponent = builder.build();
  }

  @Override
  public boolean commit() {
    // TODO den implement 
    return false;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
