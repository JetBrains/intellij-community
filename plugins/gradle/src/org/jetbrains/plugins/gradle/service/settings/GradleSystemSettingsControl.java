// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Manages gradle settings not specific to particular project (e.g. 'use wrapper' is project-level setting but 'gradle user home' is
 * a global one).
 */
public class GradleSystemSettingsControl implements ExternalSystemSettingsControl<GradleSettings> {

  private final GradleSystemSettingsControlBuilder myBuilder;


  public GradleSystemSettingsControl(@NotNull GradleSettings settings) {
    this(GradleSettingsControlProvider.get().getSystemSettingsControlBuilder(settings));
  }

  public GradleSystemSettingsControl(@NotNull GradleSystemSettingsControlBuilder builder) {
    myBuilder = builder;
  }

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
    myBuilder.fillUi(canvas, indentLevel);
  }

  @Override
  public void showUi(boolean show) {
    myBuilder.showUi(show);
  }

  @Override
  public void reset() {
    myBuilder.reset();
  }

  @Override
  public boolean isModified() {
    return myBuilder.isModified();
  }

  @Override
  public void apply(@NotNull GradleSettings settings) {
    myBuilder.apply(settings);
  }

  @Override
  public boolean validate(@NotNull GradleSettings settings) throws ConfigurationException {
    return myBuilder.validate(settings);
  }

  @Override
  public void disposeUIResources() {
    myBuilder.disposeUIResources();
  }

  public @NotNull GradleSettings getInitialSettings() {
    return myBuilder.getInitialSettings();
  }
}
