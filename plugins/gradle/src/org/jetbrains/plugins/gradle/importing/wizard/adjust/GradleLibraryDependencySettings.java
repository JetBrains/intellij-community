package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 10/24/11 3:00 PM
 */
public class GradleLibraryDependencySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibraryDependency myDependency;
  private final GradleLibrarySettings   myLibrarySettings;
  private final JCheckBox               myExportedCheckBox;
  private final JComponent              myComponent;

  public GradleLibraryDependencySettings(@NotNull GradleLibraryDependency dependency) {
    myDependency = dependency;
    myLibrarySettings = new GradleLibrarySettings(dependency.getLibrary());
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    builder.add(myLibrarySettings.getComponent(), GradleProjectSettingsBuilder.InsetSize.NONE);
    myExportedCheckBox = setupExported(builder);
    myComponent = builder.build();
    refresh();
  }

  @NotNull
  private static JCheckBox setupExported(@NotNull GradleProjectSettingsBuilder builder) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JCheckBox result = new JCheckBox();
    panel.add(new JLabel(GradleBundle.message("gradle.import.structure.settings.label.export")));
    panel.add(result);
    builder.add(panel);
    return result;
  }

  @Override
  public void refresh() {
    myLibrarySettings.refresh();
    myExportedCheckBox.setSelected(myDependency.isExported());
  }

  @Override
  public boolean validate() {
    if (!myLibrarySettings.validate()) {
      return false;
    }
    myDependency.setExported(myExportedCheckBox.isSelected());
    return true;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
