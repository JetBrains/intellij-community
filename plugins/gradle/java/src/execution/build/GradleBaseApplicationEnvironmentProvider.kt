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
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
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

    val runClass = runProfile.runClass
    val mainClass = runProfile.configurationModule.findClass(runClass) ?: return null
    val mainClassName = mainClass.qualifiedName ?: return null

    val virtualFile = mainClass.containingFile.virtualFile
    val module = runReadAction {
      ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
    } ?: return null

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
      AppUIExecutor.onUiThread().expireWith(project).submit {
        ExecutionErrorDialog.show(e, GradleInspectionBundle.message("dialog.title.cannot.use.specified.jre"), project)
      }
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
    customiseTaskExecutionsSettings(taskSettings, module)

    val executorId = executor?.id ?: DefaultRunExecutor.EXECUTOR_ID
    val environment = ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, taskSettings, executorId)
                      ?: return null
    val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings!!
    val gradleRunConfiguration = runnerAndConfigurationSettings.configuration as ExternalSystemRunConfiguration

    val gradlePath = getGradleIdentityPathOrNull(module) ?: return null
    val sourceSetName = when {
                          GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(
                            module) -> GradleProjectResolverUtil.getSourceSetName(module)
                          ModuleRootManager.getInstance(module).fileIndex.isInTestSourceContent(virtualFile) -> "test"
                          else -> "main"
                        } ?: return null
    val applicationConfiguration = runProfile as T
    val workingDir = ProgramParametersUtil.getWorkingDir(applicationConfiguration, module.project, module)?.let {
      FileUtil.toSystemIndependentName(it)
    }
    val builder = GradleInitScriptParametersBuilder(applicationConfiguration, module)
      .withWorkingDirectory(workingDir)
      .withParams(argsString(params))
      .withGradleTaskPath(gradlePath)
      .withRunAppTaskName(runAppTaskName)
      .withMainClass(mainClassName)
      .withJavaExePath(javaExePath)
      .withSourceSetName(sourceSetName)
      .withJavaModuleName(javaModuleName)

    val initScript = generateInitScript(builder.build())
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
    gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName)
    (gradleRunConfiguration as GradleRunConfiguration).isDebugServerProcess = false

    // reuse all before tasks except 'Make' as it doesn't make sense for delegated run
    gradleRunConfiguration.beforeRunTasks = RunManagerImpl.getInstanceImpl(project).getBeforeRunTasks(runProfile)
      .filter { it.providerId !== CompileStepBeforeRun.ID }
    return environment
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

    private fun findJavaModuleName(sdk: Sdk, module: JavaRunConfigurationModule, mainClass: PsiClass): String? {
      return if (JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9)) {
        runReadAction {
          DumbService.getInstance(module.project).computeWithAlternativeResolveEnabled<PsiJavaModule?, RuntimeException> {
            JavaModuleGraphUtil.findDescriptorByElement(module.findClass(mainClass.qualifiedName))
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