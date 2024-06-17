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
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiJavaModule
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetup
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Experimental
abstract class GradleBaseApplicationEnvironmentProvider<T : JavaRunConfigurationBase> : GradleExecutionEnvironmentProvider {

  abstract fun generateInitScript(params: GradleInitScriptParameters): String?

  protected open fun argsString(params: JavaParameters): String {
    return createEscapedParameters(params.programParametersList.parameters, "args") +
           createEscapedParameters(params.vmParametersList.parameters, "jvmArgs")
  }

  override fun createExecutionEnvironment(project: Project,
                                          executeRunConfigurationTask: ExecuteRunConfigurationTask,
                                          executor: Executor?): ExecutionEnvironment? {
    if (!isApplicable(executeRunConfigurationTask)) return null

    val runProfile = executeRunConfigurationTask.runProfile
    if (runProfile !is JavaRunConfigurationBase) return null

    val mainClass = runProfile.runClass ?: return null
    val module = runProfile.configurationModule.module ?: return null

    val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module) ?: return null
    val externalProjectPath = gradleModuleData.directoryToRunTask
    val settings = GradleSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath) ?: return null
    if (!projectSettings.isResolveModulePerSourceSet) {
      return null
    }
    val params = JavaParameters().apply {
      JavaParametersUtil.configureConfiguration(this, runProfile)
      this.vmParametersList.addParametersString(runProfile.vmParameters)
    }

    val taskSettings = ExternalSystemTaskExecutionSettings()
    taskSettings.isPassParentEnvs = params.isPassParentEnvs
    taskSettings.env = if (params.env.isEmpty()) emptyMap() else HashMap(params.env)
    taskSettings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    taskSettings.externalProjectPath = externalProjectPath

    val runAppTaskName = "$mainClass.main()"
    taskSettings.taskNames = listOf(gradleModuleData.getTaskPath(runAppTaskName))
    customiseTaskExecutionsSettings(taskSettings, module)

    val executorId = executor?.id ?: DefaultRunExecutor.EXECUTOR_ID
    val environment = ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, taskSettings, executorId)
                      ?: return null
    val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings!!
    val gradleRunConfiguration = runnerAndConfigurationSettings.configuration as ExternalSystemRunConfiguration

    val gradlePath = getGradleIdentityPathOrNull(module) ?: return null
    val sourceSetName = GradleProjectResolverUtil.getSourceSetName(module) ?: return null
    val workingDir = ProgramParametersUtil.getWorkingDir(runProfile, module.project, module)?.let {
      FileUtil.toSystemIndependentName(it)
    }
    val builder = GradleInitScriptParametersBuilder(runProfile, module)
      .withWorkingDirectory(workingDir)
      .withParams(argsString(params))
      .withGradleTaskPath(gradlePath)
      .withRunAppTaskName(runAppTaskName)
      .withMainClass(mainClass)
      .withSourceSetName(sourceSetName)
      .withJavaConfiguration(project, runProfile)

    val initScript = generateInitScript(builder.build())
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName)
    (gradleRunConfiguration as GradleRunConfiguration).isDebugServerProcess = false

    // reuse all before tasks except 'Make' as it doesn't make sense for delegated run
    gradleRunConfiguration.beforeRunTasks = RunManagerImpl.getInstanceImpl(project).getBeforeRunTasks(runProfile)
      .filter { it.providerId !== CompileStepBeforeRun.ID }
    return environment
  }

  private fun GradleInitScriptParametersBuilder.withJavaConfiguration(project: Project, runProfile: JavaRunConfigurationBase) = apply {
    if (getEffectiveConfiguration(runProfile, project) != null) {
      withJavaModuleName(null)
      withJavaExePath(GradleServerEnvironmentSetup.targetJavaExecutablePathMappingKey)
    }
    else {
      val jdk = resolveRunConfigurationJdk(project, runProfile)
      val type = jdk.sdkType as JavaSdkType

      val javaExePath = type.getVMExecutablePath(jdk)
                        ?: throw RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
      val javaModuleName = findJavaModuleName(jdk, runProfile.configurationModule, runProfile.runClass!!)

      withJavaModuleName(javaModuleName)
      withJavaExePath(FileUtil.toSystemIndependentName(javaExePath))
    }
  }

  private fun resolveRunConfigurationJdk(project: Project, runProfile: JavaRunConfigurationBase): Sdk {
    try {
      return JavaParametersUtil.createProjectJdk(project, runProfile.alternativeJrePath)
             ?: throw RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
    }
    catch (e: CantRunException) {
      @Suppress("IncorrectParentDisposable")
      AppUIExecutor.onUiThread().expireWith(project).submit {
        ExecutionErrorDialog.show(e, GradleInspectionBundle.message("dialog.title.cannot.use.specified.jre"), project)
      }
      throw RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
    }
  }

  protected open fun customiseTaskExecutionsSettings(taskSettings: ExternalSystemTaskExecutionSettings, module: Module) {}

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

    private fun findJavaModuleName(sdk: Sdk, module: JavaRunConfigurationModule, mainClass: String): String? {
      return if (JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9)) {
        runReadAction {
          DumbService.getInstance(module.project).computeWithAlternativeResolveEnabled<PsiJavaModule?, RuntimeException> {
            JavaModuleGraphUtil.findDescriptorByElement(module.findClass(mainClass))
          }?.name
        } ?: return null
      }
      else null
    }
  }

  private class GradleInitScriptParametersBuilder(val configuration: JavaRunConfigurationBase, val module: Module) {
    private var workingDirectory: String? = null
    private lateinit var params: String
    private lateinit var gradleTaskPath: String
    private lateinit var runAppTaskName: String
    private lateinit var mainClass: String
    private lateinit var javaExePath: String
    private lateinit var sourceSetName: String
    private var javaModuleName: String? = null

    fun build(): GradleInitScriptParameters {
      return GradleInitScriptParametersImpl(configuration,
                                            module,
                                            workingDirectory,
                                            params,
                                            gradleTaskPath,
                                            runAppTaskName,
                                            mainClass,
                                            javaExePath,
                                            sourceSetName,
                                            javaModuleName)
    }

    fun withWorkingDirectory(workingDirectory: String?): GradleInitScriptParametersBuilder {
      this.workingDirectory = workingDirectory
      return this
    }

    fun withParams(params: String): GradleInitScriptParametersBuilder {
      this.params = params
      return this
    }

    fun withGradleTaskPath(gradleTaskPath: String): GradleInitScriptParametersBuilder {
      this.gradleTaskPath = gradleTaskPath
      return this
    }

    fun withRunAppTaskName(runAppTaskName: String): GradleInitScriptParametersBuilder {
      this.runAppTaskName = runAppTaskName
      return this
    }

    fun withMainClass(mainClass: String): GradleInitScriptParametersBuilder {
      this.mainClass = mainClass
      return this
    }

    fun withJavaExePath(javaExePath: String): GradleInitScriptParametersBuilder {
      this.javaExePath = javaExePath
      return this
    }

    fun withSourceSetName(sourceSetName: String): GradleInitScriptParametersBuilder {
      this.sourceSetName = sourceSetName
      return this
    }

    fun withJavaModuleName(javaModuleName: String?): GradleInitScriptParametersBuilder {
      this.javaModuleName = javaModuleName
      return this
    }
  }

  private class GradleInitScriptParametersImpl(
    override val configuration: JavaRunConfigurationBase,
    override val module: Module,
    override val workingDirectory: String?,
    override val params: String,
    override val gradleTaskPath: String,
    override val runAppTaskName: String,
    override val mainClass: String,
    override val javaExePath: String,
    override val sourceSetName: String,
    override val javaModuleName: String?,
  ) : GradleInitScriptParameters
}