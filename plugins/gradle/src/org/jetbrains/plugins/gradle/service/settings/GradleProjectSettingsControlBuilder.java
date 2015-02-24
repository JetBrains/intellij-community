/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.service.settings.ExternalSystemSettingsControlCustomizer;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * @author Vladislav.Soroka
 * @since 2/24/2015
 */
public interface GradleProjectSettingsControlBuilder {

  GradleProjectSettings getInitialSettings();

  IdeaGradleProjectSettingsControlBuilder addGradleHomeComponents(PaintAwarePanel content, int indentLevel);

  IdeaGradleProjectSettingsControlBuilder addGradleJdkComponents(PaintAwarePanel content, int indentLevel);

  IdeaGradleProjectSettingsControlBuilder addGradleChooserComponents(PaintAwarePanel content, int indentLevel);

  void disposeUIResources();

  boolean validate(GradleProjectSettings settings) throws ConfigurationException;

  void apply(GradleProjectSettings settings);

  boolean isModified(GradleProjectSettings settings);

  void reset(Project project, GradleProjectSettings settings, boolean isDefaultModuleCreation);

  void createAndFillControls(PaintAwarePanel content, int indentLevel);

  void update(String linkedProjectPath, GradleProjectSettings settings, boolean isDefaultModuleCreation);

  @Nullable
  ExternalSystemSettingsControlCustomizer getExternalSystemSettingsControlCustomizer();
}
