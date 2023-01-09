// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.getEffectiveConfiguration
import com.intellij.execution.util.ExecutionErrorDialog
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaModule
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetup
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Experimental
abstract class GradleBaseApplicationEnvironmentProvider<T : JavaRunConfigurationBase> : GradleExecutionEnvironmentProvider {

  abstract fun generateInitScript(
    applicationConfiguration: T, module: Module, params: JavaParameters,
    gradleTaskPath: String, runAppTaskName: String, mainClass: PsiClass, javaExePath: String,
    sourceSetName: String, javaModuleName: String?
  ): String?

  override fun createExecutionEnvironment(project: Project,
                                          executeRunConfigurationTask: ExecuteRunConfigurationTask,
                                          executor: Executor?): ExecutionEnvironment? {
    if (!isApplicable(executeRunConfigurationTask)) return null

    val runProfile = executeRunConfigurationTask.runProfile
    if (runProfile !is JavaRunConfigurationBase) return null

    val runClass = runProfile.runClass
    val mainClass = runProfile.configurationModule.findClass(runClass) ?: return null

    val virtualFile = mainClass.containingFile.virtualFile
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return null

    val params = JavaParameters().apply {
      JavaParametersUtil.configureConfiguration(this, runProfile)
      this.vmParametersList.addParametersString(runProfile.vmParameters)
    }

    val javaModuleName: String?
    val javaExePath: String
    try {
      if (getEffectiveConfiguration(runProfile, project) != null) {
        javaModuleName = null
        javaExePath = GradleServerEnvironmentSetup.targetJavaExecutablePathMappingKey
      }
      else {
        val jdk = JavaParametersUtil.createProjectJdk(project, runProfile.alternativeJrePath)
                  ?: throw RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
        val type = jdk.sdkType
        if (type !is JavaSdkType) throw RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
        javaExePath = (type as JavaSdkType).getVMExecutablePath(jdk)?.let {
          FileUtil.toSystemIndependentName(it)
        } ?: throw RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
        javaModuleName = findJavaModuleName(jdk, runProfile.configurationModule, mainClass)
      }
    }
    catch (e: CantRunException) {
      ExecutionErrorDialog.show(e, GradleInspectionBundle.message("dialog.title.cannot.use.specified.jre"), project)
      throw RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
    }
    val taskSettings = ExternalSystemTaskExecutionSettings()
    taskSettings.isPassParentEnvs = params.isPassParentEnvs
    taskSettings.env = if (params.env.isEmpty()) emptyMap() else HashMap(params.env)
    taskSettings.externalSystemIdString = GradleConstants.SYSTEM_ID.id

    val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module)
    taskSettings.externalProjectPath = gradleModuleData?.directoryToRunTask ?: GradleRunnerUtil.resolveProjectPath(module)
    val runAppTaskName = mainClass.name!! + ".main()"
    taskSettings.taskNames = listOf((gradleModuleData?.getTaskPath(runAppTaskName) ?: runAppTaskName))

    val executorId = executor?.id ?: DefaultRunExecutor.EXECUTOR_ID
    val environment = ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, taskSettings, executorId)
                      ?: return null
    val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings!!
    val gradleRunConfiguration = runnerAndConfigurationSettings.configuration as ExternalSystemRunConfiguration

    val gradlePath = GradleProjectResolverUtil.getGradlePath(module) ?: return null
    val sourceSetName = when {
                          GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(
                            module) -> GradleProjectResolverUtil.getSourceSetName(module)
                          ModuleRootManager.getInstance(module).fileIndex.isInTestSourceContent(virtualFile) -> "test"
                          else -> "main"
                        } ?: return null

    val initScript = generateInitScript(runProfile as T, module, params, gradlePath,
                                        runAppTaskName, mainClass, javaExePath, sourceSetName, javaModuleName)
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName)
    (gradleRunConfiguration as GradleRunConfiguration).isScriptDebugEnabled = false

    // reuse all before tasks except 'Make' as it doesn't make sense for delegated run
    gradleRunConfiguration.beforeRunTasks = RunManagerImpl.getInstanceImpl(project).getBeforeRunTasks(runProfile)
      .filter { it.providerId !== CompileStepBeforeRun.ID }
    return environment
  }

  companion object {
    fun createEscapedParameters(parameters: List<String>, prefix: String): String {
      val result = StringBuilder()
      for (parameter in parameters) {
        if (StringUtil.isEmpty(parameter)) continue
        val escaped = StringUtil.escapeChars(parameter, '\\', '"', '\'')
        result.append(prefix).append(" '").append(escaped).append("'\n")
      }
      return result.toString()
    }

    private fun findJavaModuleName(sdk: Sdk, module: JavaRunConfigurationModule, mainClass: PsiClass): String? {
      return if (JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9)) {
        runReadAction {
          DumbService.getInstance(module.project).computeWithAlternativeResolveEnabled<PsiJavaModule, RuntimeException> {
            JavaModuleGraphUtil.findDescriptorByElement(module.findClass(mainClass.qualifiedName))
          }?.name
        } ?: return null
      }
      else null
    }
  }
}