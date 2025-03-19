// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaFxSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final JavaFxSettings mySettings;
  private JavaFxSettingsConfigurableUi myPanel;

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
  public JComponent createComponent() {
    myPanel = new JavaFxSettingsConfigurableUi();
    return myPanel.getPanel();
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
}
