// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.settings;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class SpellCheckerSettingsManager implements SearchableConfigurable, Configurable.NoScroll, Configurable.WithEpDependencies {
  private SpellCheckerSettingsPane settingsPane;
  private final SpellCheckerSettings settings;
  private final Project project;

  public SpellCheckerSettingsManager(Project project) {
    this.project = project;
    this.settings = SpellCheckerSettings.getInstance(project);
  }

  @Override
  public @Nls String getDisplayName() {
    return SpellCheckerBundle.message("spelling");
  }

  @Override
  public @NonNls @NotNull String getHelpTopic() {
    return "reference.settings.ide.settings.spelling";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    if (settingsPane == null) {
      settingsPane = new SpellCheckerSettingsPane(settings, project);
    }
    return settingsPane.getPane();
  }

  @Override
  public boolean isModified() {
    return settingsPane == null || settingsPane.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (settingsPane != null) {
      settingsPane.apply();
    }
  }

  @Override
  public void reset() {
    if (settingsPane != null) {
      settingsPane.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    settingsPane = null;
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(CustomDictionaryProvider.EP_NAME);
  }
}
