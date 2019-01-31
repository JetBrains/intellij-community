// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.task.ExecuteRunConfigurationTask;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collections;
import java.util.List;

/**
 * TODO take into account applied 'application' gradle plugins or existing JavaExec tasks
 *
 * @author Vladislav.Soroka
 */
public class GradleApplicationEnvironmentProvider implements GradleExecutionEnvironmentProvider {

  @Override
  public boolean isApplicable(ExecuteRunConfigurationTask task) {
    return task.getRunProfile() instanceof ApplicationConfiguration;
  }

  @Override
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

    String javaModuleName = null;
    String javaExePath = null;
    try {
      final Sdk jdk = JavaParametersUtil.createProjectJdk(project, applicationConfiguration.getAlternativeJrePath());
      if (jdk == null) throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      final SdkTypeId type = jdk.getSdkType();
      if (!(type instanceof JavaSdkType)) throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      javaExePath = ((JavaSdkType)type).getVMExecutablePath(jdk);
      if (javaExePath == null) throw new RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
      javaExePath = FileUtil.toSystemIndependentName(javaExePath);
      javaModuleName = findJavaModuleName(jdk, applicationConfiguration.getConfigurationModule(), mainClass);
    }
    catch (CantRunException e) {
      ExecutionErrorDialog.show(e, "Cannot use specified JRE", project);
    }

    ExternalSystemTaskExecutionSettings taskSettings = new ExternalSystemTaskExecutionSettings();
    taskSettings.setPassParentEnvs(params.isPassParentEnvs());
    taskSettings.setEnv(params.getEnv().isEmpty() ? Collections.emptyMap() : new THashMap<>(params.getEnv()));
    taskSettings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    String projectPath = GradleRunnerUtil.resolveProjectPath(module);
    taskSettings.setExternalProjectPath(projectPath);
    final String runAppTaskName = mainClass.getName() + ".main()";
    taskSettings.setTaskNames(Collections.singletonList(runAppTaskName));

    String executorId = executor == null ? DefaultRunExecutor.EXECUTOR_ID : executor.getId();
    ExecutionEnvironment environment =
      ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, taskSettings, executorId);
    if (environment != null) {
      RunnerAndConfigurationSettings runnerAndConfigurationSettings = environment.getRunnerAndConfigurationSettings();
      assert runnerAndConfigurationSettings != null;
      ExternalSystemRunConfiguration gradleRunConfiguration =
        (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();

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

      String parametersString = createEscapedParameters(params.getProgramParametersList().getParameters(), "args");
      String vmParametersString = createEscapedParameters(params.getVMParametersList().getParameters(), "jvmArgs");

      // @formatter:off
      @Language("Groovy")
      String initScript = "allprojects {\n" +
                          "    afterEvaluate { project ->\n" +
                          "      if(project.path == '" + gradlePath + "' && project?.convention?.findPlugin(JavaPluginConvention)) {\n" +
                          "         project.tasks.create(name: '" + runAppTaskName + "', overwrite: true, type: JavaExec) {\n" +
                                      (javaExePath == null ? "" :
                          "           executable = '" + javaExePath + "'\n") +
                          "           classpath = project.sourceSets.'" + sourceSetName + "'.runtimeClasspath\n" +
                          "           main = '" + mainClass.getQualifiedName() + "'\n" +
                                      parametersString +
                                      vmParametersString +
                                      (StringUtil.isNotEmpty(workingDir) ?
                          "           workingDir = '" + workingDir + "'\n" : "") +
                          "           standardInput = System.in\n" +
                                      (javaModuleName == null ? "" :
                          "           inputs.property('moduleName', '" + javaModuleName + "')\n" +
                          "           doFirst {\n" +
                          "             jvmArgs += [\n" +
                          "               '--module-path', classpath.asPath,\n" +
                          "               '--module', '" + javaModuleName + "/" + mainClass.getQualifiedName() + "'\n" +
                          "             ]\n" +
                          "             classpath = files()\n"+
                          "           }\n") +
                          "         }\n" +
                          "      }\n" +
                          "    }\n" +
                          "}\n";
      // @formatter:on

      gradleRunConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);
      gradleRunConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName);

      // reuse all before tasks except 'Make' as it doesn't make sense for delegated run
      gradleRunConfiguration.setBeforeRunTasks(ContainerUtil.filter(
        RunManagerImpl.getInstanceImpl(project).getBeforeRunTasks(applicationConfiguration),
        task -> task.getProviderId() != CompileStepBeforeRun.ID));
      return environment;
    }
    else {
      return null;
    }
  }

  private static String createEscapedParameters(List<String> parameters, String prefix) {
    StringBuilder result = new StringBuilder();
    for (String parameter: parameters) {
      if (StringUtil.isEmpty(parameter)) continue;
      String escaped = StringUtil.escapeChars(parameter, '\\', '"', '\'');
      result.append(prefix).append(" '").append(escaped).append("'\n");
    }
    return result.toString();
  }

  @Nullable
  private static String findJavaModuleName(Sdk sdk, JavaRunConfigurationModule module, PsiClass mainClass) {
    if (JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9)) {
      PsiJavaModule mainModule = DumbService.getInstance(module.getProject()).computeWithAlternativeResolveEnabled(
        () -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(mainClass.getQualifiedName())));
      if (mainModule != null) {
        return mainModule.getName();
      }
    }
    return null;
  }
}
