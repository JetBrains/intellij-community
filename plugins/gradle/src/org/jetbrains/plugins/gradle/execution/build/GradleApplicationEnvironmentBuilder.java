/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.activity.RunActivity;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collections;

/**
 * TODO take into account applied 'application' gradle plugins or existing JavaExec tasks
 *
 * @author Vladislav.Soroka
 * @since 6/21/2016
 */
public class GradleApplicationEnvironmentBuilder {

  @Nullable
  public ExecutionEnvironment build(@NotNull Project project, @NotNull RunActivity activity) {
    if (!(activity.getRunProfile() instanceof ApplicationConfiguration)) return null;

    ApplicationConfiguration applicationConfiguration = (ApplicationConfiguration)activity.getRunProfile();
    PsiClass mainClass = applicationConfiguration.getMainClass();
    if(mainClass == null) return null;

    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(mainClass.getContainingFile().getVirtualFile());
    if (module == null) return null;

    String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
    String gradleProjectPath = projectId;
    int lastPathDelimiterIndex = gradleProjectPath == null ? -1 : gradleProjectPath.lastIndexOf(':');
    if (gradleProjectPath == null || !gradleProjectPath.startsWith(":")) {
      gradleProjectPath = ":";
    } else {
      if (!gradleProjectPath.equals(":")) {
        gradleProjectPath = gradleProjectPath.substring(0, lastPathDelimiterIndex);
      }
    }

    final JavaParameters params = new JavaParameters();
    JavaParametersUtil.configureConfiguration(params, applicationConfiguration);
    params.getVMParametersList().addParametersString(applicationConfiguration.getVMParameters());

    StringBuilder parametersString = new StringBuilder();
    for (String parameter : params.getProgramParametersList().getParameters()) {
      parametersString.append("args '").append(parameter).append("'\n");
    }

    StringBuilder vmParametersString = new StringBuilder();
    for (String parameter : params.getVMParametersList().getParameters()) {
      vmParametersString.append("jvmArgs '").append(parameter).append("'\n");
    }


    ExternalSystemTaskExecutionSettings taskSettings = new ExternalSystemTaskExecutionSettings();
    taskSettings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    taskSettings.setExternalProjectPath(externalProjectPath);
    final String runAppTaskName = "run " + mainClass.getName();
    taskSettings.setTaskNames(Collections.singletonList(runAppTaskName));

    String executorId = activity.getExecutor() == null ? DefaultRunExecutor.EXECUTOR_ID : activity.getExecutor().getId();
    final Pair<ProgramRunner, ExecutionEnvironment> environmentPair =
      ExternalSystemUtil.createRunner(taskSettings, executorId, project, GradleConstants.SYSTEM_ID);
    if (environmentPair != null) {
      RunnerAndConfigurationSettings runnerAndConfigurationSettings = environmentPair.second.getRunnerAndConfigurationSettings();
      assert runnerAndConfigurationSettings != null;
      ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();

      String sourceSetName = projectId == null || lastPathDelimiterIndex == -1 ? "main" : projectId.substring(lastPathDelimiterIndex + 1);
      @Language("Groovy")
      String initScript = "projectsEvaluated {\n" +
                          "  rootProject.allprojects {\n" +
                          "    if(project.path == '" + gradleProjectPath + "' && project.sourceSets) {\n" +
                          "      project.tasks.create(name: '" + runAppTaskName + "', overwrite: true, type: JavaExec) {\n" +
                          "        classpath = project.sourceSets.'" + sourceSetName + "'.runtimeClasspath\n" +
                          "        main = '" + mainClass.getQualifiedName() + "'\n" +
                          parametersString.toString() +
                          vmParametersString.toString() +
                          "      }\n" +
                          "    }\n" +
                          "  }\n" +
                          "}\n";

      runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);
      return environmentPair.second;
    }
    else {
      return null;
    }
  }
}
