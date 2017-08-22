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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.task.ExecuteRunConfigurationTask;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collections;

/**
 * TODO take into account applied 'application' gradle plugins or existing JavaExec tasks
 *
 * @author Vladislav.Soroka
 * @since 6/21/2016
 */
public class GradleApplicationEnvironmentProvider implements GradleExecutionEnvironmentProvider {

  @Override
  public boolean isApplicable(ExecuteRunConfigurationTask task) {
    return task.getRunProfile() instanceof ApplicationConfiguration;
  }

  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask executeRunConfigurationTask,
                                                         @Nullable Executor executor) {
    if (!isApplicable(executeRunConfigurationTask)) return null;

    ApplicationConfiguration applicationConfiguration = (ApplicationConfiguration)executeRunConfigurationTask.getRunProfile();
    PsiClass mainClass = applicationConfiguration.getMainClass();
    if (mainClass == null) return null;

    VirtualFile virtualFile = mainClass.getContainingFile().getVirtualFile();
    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile);
    if (module == null) return null;

    final JavaParameters params = new JavaParameters();
    JavaParametersUtil.configureConfiguration(params, applicationConfiguration);
    params.getVMParametersList().addParametersString(applicationConfiguration.getVMParameters());

    String javaExePath = null;
    try {
      final Sdk jdk = JavaParametersUtil.createProjectJdk(project, applicationConfiguration.getAlternativeJrePath());
      if (jdk == null) throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      final SdkTypeId type = jdk.getSdkType();
      if (!(type instanceof JavaSdkType)) throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      javaExePath = ((JavaSdkType)type).getVMExecutablePath(jdk);
      if (javaExePath == null) throw new RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
      javaExePath = FileUtil.toSystemIndependentName(javaExePath);
    }
    catch (CantRunException e) {
      ExecutionErrorDialog.show(e, "Cannot use specified JRE", project);
    }

    StringBuilder parametersString = new StringBuilder();
    for (String parameter : params.getProgramParametersList().getParameters()) {
      if (StringUtil.isEmpty(parameter)) continue;
      String escaped = StringUtil.escapeChars(parameter, '\\', '"', '\'');
      parametersString.append("args '").append(escaped).append("'\n");
    }

    StringBuilder vmParametersString = new StringBuilder();
    for (String parameter : params.getVMParametersList().getParameters()) {
      vmParametersString.append("jvmArgs '").append(parameter).append("'\n");
    }

    ExternalSystemTaskExecutionSettings taskSettings = new ExternalSystemTaskExecutionSettings();
    taskSettings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    taskSettings.setExternalProjectPath(ExternalSystemApiUtil.getExternalProjectPath(module));
    final String runAppTaskName = "run " + mainClass.getName();
    taskSettings.setTaskNames(Collections.singletonList(runAppTaskName));

    String executorId = executor == null ? DefaultRunExecutor.EXECUTOR_ID : executor.getId();
    ExecutionEnvironment environment =
      ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, taskSettings, executorId);
    if (environment != null) {
      RunnerAndConfigurationSettings runnerAndConfigurationSettings = environment.getRunnerAndConfigurationSettings();
      assert runnerAndConfigurationSettings != null;
      ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();

      final String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
      if (gradlePath == null) return null;
      final String sourceSetName;
      if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module))) {
        sourceSetName = GradleProjectResolverUtil.getSourceSetName(module);
      }
      else {
        sourceSetName = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile) ? "test" : "main";
      }
      if (sourceSetName == null) return null;

      String workingDir = ProgramParametersUtil.getWorkingDir(applicationConfiguration, project, module);
      workingDir = workingDir == null ? null : FileUtil.toSystemIndependentName(workingDir);
      @Language("Groovy")
      String initScript = "allprojects {\n" +
                          "    afterEvaluate { project ->\n" +
                          "      if(project.path == '" + gradlePath + "' && project?.convention?.findPlugin(JavaPluginConvention)) {\n" +
                          "         project.tasks.create(name: '" + runAppTaskName + "', overwrite: true, type: JavaExec) {\n" +
                          (javaExePath != null ?
                           "          executable = '" + javaExePath + "'\n" : "") +
                          "           classpath = project.sourceSets.'" + sourceSetName + "'.runtimeClasspath\n" +
                          "           main = '" + mainClass.getQualifiedName() + "'\n" +
                          parametersString.toString() +
                          vmParametersString.toString() +
                          (StringUtil.isNotEmpty(workingDir) ?
                           "          workingDir = '" + workingDir + "'\n" : "") +
                          "           standardInput = System.in\n" +
                          "         }\n" +
                          "      }\n" +
                          "    }\n" +
                          "}\n";

      runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);
      return environment;
    }
    else {
      return null;
    }
  }
}
