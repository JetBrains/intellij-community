package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleProject;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages settings of {@link GradleProject gradle project} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:58 PM
 */
public class GradleProjectSettings implements GradleProjectStructureNodeSettings {

  private final JComponent    myComponent;
  private final GradleProject myProject;
  private final JLabel        myNameErrorLabel;

  public GradleProjectSettings(@NotNull GradleProject project) {
    myProject = project;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    
    myNameErrorLabel = GradleAdjustImportSettingsUtil.configureNameControl(builder, project);

    JComboBox languageLevelComboBox = new JComboBox();
    builder.add("gradle.import.structure.settings.label.language.level", languageLevelComboBox);
    final Map<Object, LanguageLevel> levels = new HashMap<Object, LanguageLevel>();
    for (LanguageLevel level : LanguageLevel.values()) {
      levels.put(level.getPresentableText(), level);
      languageLevelComboBox.addItem(level.getPresentableText());
    }
    languageLevelComboBox.setSelectedItem(project.getLanguageLevel().getPresentableText());
    languageLevelComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myProject.setLanguageLevel(levels.get(e.getItem()));
      }
    });
    
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return GradleAdjustImportSettingsUtil.validate(myProject, myNameErrorLabel);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
