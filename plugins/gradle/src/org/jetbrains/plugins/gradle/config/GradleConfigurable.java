/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.config;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleIcons;
import org.jetbrains.plugins.gradle.util.GradleLibraryManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author peter
 */
public class GradleConfigurable implements SearchableConfigurable {

  @NonNls public static final String HELP_TOPIC = "reference.settingsdialog.project.gradle";
  
  private final GradleLibraryManager myLibraryManager = GradleLibraryManager.INSTANCE;
  private final Project myProject;

  private JPanel            myComponent;
  private NamePathComponent myPathComponent;
  
  public GradleConfigurable(@Nullable Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public JComponent createComponent() {
    myComponent = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;

    myPathComponent = new NamePathComponent(
      "", GradleBundle.message("gradle.import.text.home.path"), GradleBundle.message("gradle.import.text.home.path"), "",
      false,
      false
    );
    myPathComponent.setNameComponentVisible(false);
    myComponent.add(myPathComponent, constraints);
    myComponent.add(Box.createVerticalGlue());
    return myComponent;
  }

  @Override
  public boolean isModified() {
    if (!myPathComponent.isPathChangedByUser()) {
      return false;
    }
    String newPath = myPathComponent.getPath();
    String oldPath = GradleSettings.getInstance(myProject).GRADLE_HOME;
    boolean modified = newPath == null ? oldPath == null : !newPath.equals(oldPath);
    if (modified) {
      useNormalColorForPath();
    } 
    return modified;
  }

  @Override
  public void apply() throws ConfigurationException {
    useNormalColorForPath();
    GradleSettings.getInstance(myProject).GRADLE_HOME = myPathComponent.getPath();
  }

  @Override
  public void reset() {
    useNormalColorForPath();
    String valueToUse = GradleSettings.getInstance(myProject).GRADLE_HOME;
    if (!StringUtil.isEmpty(valueToUse)) {
      myPathComponent.setPath(valueToUse);
      return; 
    }
    deduceGradleHomeIfPossible();  
  }

  private void useNormalColorForPath() {
    myPathComponent.getPathComponent().setForeground(UIManager.getColor("TextField.foreground"));
  }
  
  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    File gradleHome = myLibraryManager.getGradleHome(myProject);
    if (gradleHome == null) {
      return;
    }
    myPathComponent.setPath(gradleHome.getPath());
    myPathComponent.getPathComponent().setForeground(UIManager.getColor("TextField.inactiveForeground"));
  }
  
  @Override
  public void disposeUIResources() {
    myComponent = null;
    myPathComponent = null;
  }

  public Icon getIcon() {
    return GradleIcons.GRADLE_ICON;
  }

  @NotNull
  public String getHelpTopic() {
    return HELP_TOPIC;
  }
}