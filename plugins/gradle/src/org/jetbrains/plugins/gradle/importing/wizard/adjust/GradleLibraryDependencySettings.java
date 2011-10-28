package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 10/24/11 3:00 PM
 */
public class GradleLibraryDependencySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibraryDependency myDependency;
  private final GradleLibrarySettings   myLibrarySettings;
  private final JCheckBox               myExportedCheckBox;
  private final JComboBox               myScopeComboBox;
  private final JComponent              myComponent;

  public GradleLibraryDependencySettings(@NotNull GradleLibraryDependency dependency) {
    myDependency = dependency;
    myLibrarySettings = new GradleLibrarySettings(dependency.getLibrary());
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    builder.setKeyAndValueControlsOnSameRow(true);
    builder.add(myLibrarySettings.getComponent(), GradleProjectSettingsBuilder.InsetSize.NONE);
    myExportedCheckBox = setupExported(builder);
    myScopeComboBox = setupScope(builder);
    myComponent = builder.build();
    refresh();
  }

  @NotNull
  private static JCheckBox setupExported(@NotNull GradleProjectSettingsBuilder builder) {
    JCheckBox result = new JCheckBox();
    builder.add("gradle.import.structure.settings.label.export", result);
    return result;
  }
  
  @NotNull
  private static JComboBox setupScope(@NotNull GradleProjectSettingsBuilder builder) {
    JComboBox result = new JComboBox(DependencyScope.values());
    builder.add("gradle.import.structure.settings.label.scope", result);
    return result;
  }
  
  @Override
  public void refresh() {
    myLibrarySettings.refresh();
    myExportedCheckBox.setSelected(myDependency.isExported());
    myScopeComboBox.setSelectedItem(myDependency.getScope());
  }

  @Override
  public boolean validate() {
    if (!myLibrarySettings.validate()) {
      return false;
    }
    myDependency.setExported(myExportedCheckBox.isSelected());
    myDependency.setScope((DependencyScope)myScopeComboBox.getSelectedItem());
    return true;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
