/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 2/14/13
 */
public class JavaFxSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final JavaFxSettings mySettings;
  private JavaFxConfigurablePanel myPanel;


  public JavaFxSettingsConfigurable(JavaFxSettings settings) {
    mySettings = settings;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "JavaFX";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "preferences.JavaFX";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanel = new JavaFxConfigurablePanel();
    return myPanel.myWholePanel;
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified(mySettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply(mySettings);
  }

  @Override
  public void reset() {
    myPanel.reset(mySettings);
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  public static FileChooserDescriptor createSceneBuilderDescriptor() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor();
    descriptor.setTitle("SceneBuilder Configuration");
    descriptor.setDescription("Select path to SceneBuilder executable");
    return descriptor;
  }

  public static class JavaFxConfigurablePanel {
    private TextFieldWithBrowseButton myPathField;
    private JPanel myWholePanel;

    public JavaFxConfigurablePanel() {
      final FileChooserDescriptor descriptor = createSceneBuilderDescriptor();
      myPathField.addBrowseFolderListener(descriptor.getTitle(), descriptor.getDescription(), null, descriptor);
    }

    private void reset(JavaFxSettings settings) {
      final String pathToSceneBuilder = settings.getPathToSceneBuilder();
      if (pathToSceneBuilder != null) {
        myPathField.setText(FileUtil.toSystemDependentName(pathToSceneBuilder));
      }
    }

    private void apply(JavaFxSettings settings) {
      settings.setPathToSceneBuilder(FileUtil.toSystemIndependentName(myPathField.getText().trim()));
    }

    private boolean isModified(JavaFxSettings settings) {
      final String pathToSceneBuilder = settings.getPathToSceneBuilder();
      return !Comparing.strEqual(FileUtil.toSystemIndependentName(myPathField.getText().trim()),
                                 pathToSceneBuilder != null ? pathToSceneBuilder.trim() : null);
    }
  }
}
