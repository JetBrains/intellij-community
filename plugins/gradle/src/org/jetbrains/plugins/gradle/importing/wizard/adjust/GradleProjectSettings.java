package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleProject;
import org.jetbrains.plugins.gradle.util.GradleUiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Manages settings of {@link GradleProject gradle project} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:58 PM
 */
public class GradleProjectSettings implements GradleProjectStructureNodeSettings {

  private final JComboBox  myLanguageLevelComboBox = new JComboBox(LanguageLevel.values());
  
  private final JComponent    myComponent;
  private final GradleProject myProject;

  public GradleProjectSettings(@NotNull GradleProject project) {
    myProject = project;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    GradleUiUtil.configureNameControl(builder, project);
    builder.add("gradle.import.structure.settings.label.language.level", myLanguageLevelComboBox);
    myLanguageLevelComboBox.setSelectedItem(project.getLanguageLevel());
    
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
