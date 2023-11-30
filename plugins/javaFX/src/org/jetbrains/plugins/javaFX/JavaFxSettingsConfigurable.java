// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.ide.IdeBundle;
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

public final class JavaFxSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final JavaFxSettings mySettings;
  private JavaFxConfigurablePanel myPanel;

  public JavaFxSettingsConfigurable() {
    mySettings = JavaFxSettings.getInstance();
  }

  @Override
  public @Nls String getDisplayName() {
    return IdeBundle.message("configurable.JavaFxSettingsConfigurable.display.name");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "preferences.JavaFX";
  }

  @Override
  public @Nullable JComponent createComponent() {
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

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  public static FileChooserDescriptor createSceneBuilderDescriptor() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor();
    descriptor.setTitle(JavaFXBundle.message("javafx.settings.configurable.scene.builder.configuration.title"));
    descriptor.setDescription(JavaFXBundle.message("javafx.settings.configurable.scene.builder.configuration.description"));
    return descriptor;
  }

  public static final class JavaFxConfigurablePanel {
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
