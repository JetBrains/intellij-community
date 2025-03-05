// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class GradleConfigurable extends AbstractExternalSystemConfigurable<GradleProjectSettings, GradleSettingsListener, GradleSettings> {

  public static final String DISPLAY_NAME = GradleConstants.SYSTEM_ID.getReadableName();
  public static final String ID = "reference.settingsdialog.project.gradle";
  public static final @NonNls String HELP_TOPIC = ID;

  public GradleConfigurable(@NotNull Project project) {
    super(project, GradleConstants.SYSTEM_ID);
  }

  @Override
  protected @NotNull ExternalSystemSettingsControl<GradleProjectSettings> createProjectSettingsControl(@NotNull GradleProjectSettings settings) {
    return new GradleProjectSettingsControl(settings);
  }

  @Override
  protected @Nullable ExternalSystemSettingsControl<GradleSettings> createSystemSettingsControl(@NotNull GradleSettings settings) {
    return new GradleSystemSettingsControl(settings);
  }

  @Override
  protected @NotNull GradleProjectSettings newProjectSettings() {
    return new GradleProjectSettings();
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return HELP_TOPIC;
  }
}
