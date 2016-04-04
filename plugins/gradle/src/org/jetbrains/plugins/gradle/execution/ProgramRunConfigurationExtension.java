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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Vladislav.Soroka
 * @since 12/28/2015
 */
public class ProgramRunConfigurationExtension extends RunConfigurationExtension {
  @Override
  public <T extends RunConfigurationBase> void updateJavaParameters(T configuration,
                                                                    JavaParameters params,
                                                                    RunnerSettings runnerSettings) throws ExecutionException {
  }

  @Override
  protected void validateConfiguration(@NotNull RunConfigurationBase configuration, boolean isExecution) throws Exception {
    super.validateConfiguration(configuration, isExecution);
    if (configuration instanceof ModuleBasedConfiguration && configuration instanceof CommonProgramRunConfigurationParameters) {
      final String workingDirectory = ((CommonProgramRunConfigurationParameters)configuration).getWorkingDirectory();
      if (("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory)) {
        final RunConfigurationModule runConfigurationModule = ((ModuleBasedConfiguration)configuration).getConfigurationModule();
        final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(runConfigurationModule.getModule());
        if (StringUtil.isNotEmpty(projectPath)) {
          ((CommonProgramRunConfigurationParameters)configuration).setWorkingDirectory(projectPath);
        }
      }
    }
  }

  @Override
  protected void readExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element)
    throws InvalidDataException {
  }

  @Nullable
  @Override
  protected <P extends RunConfigurationBase> SettingsEditor<P> createEditor(@NotNull P configuration) {
    return null;
  }

  @Nullable
  @Override
  protected String getEditorTitle() {
    return null;
  }

  @Override
  protected boolean isApplicableFor(@NotNull RunConfigurationBase configuration) {
    if (configuration instanceof ModuleBasedConfiguration && configuration instanceof CommonProgramRunConfigurationParameters) {
      final RunConfigurationModule runConfigurationModule = ((ModuleBasedConfiguration)configuration).getConfigurationModule();
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, runConfigurationModule.getModule());
    }
    return false;
  }
}
