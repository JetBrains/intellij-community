// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.performancePlugin.CreateMavenProjectCommand.Companion.getNewProject
import org.jetbrains.idea.maven.performancePlugin.CreateMavenProjectCommand.Companion.runNewProject
import org.jetbrains.plugins.gradle.performanceTesting.dto.NewGradleProjectDto
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.javaGradleData
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

/**
 * The command creates a new Gradle groovy/kotlin project.
 * The project also could be added as module (linked to the current project)
 * Argument is serialized [NewGradleProjectDto] as json
 * You should have a focus (on file, for example) if you are creating a new project.
 */
class CreateGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "createGradleProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }


  override suspend fun doExecute(context: PlaybackContext) {
    val disposable = Disposer.newDisposable()

    try {
      val project = context.project
      val newGradleProjectDto = deserializeOptionsFromJson(extractCommandArgument(PREFIX), NewGradleProjectDto::class.java)
      val parentModule = ModuleManager.getInstance(project).modules.firstOrNull { it.name == newGradleProjectDto.parentModuleName }
      val projectPath = (parentModule?.guessModuleDir()?.path?.let { Path.of(it) } ?: Path.of(project.basePath!!)).resolve(
        newGradleProjectDto.projectName)
      val modulesConfigurator = if (newGradleProjectDto.asModule)
        ModulesConfigurator(project, ProjectStructureConfigurable.getInstance(project))
      else
        null

      val wizardContext = WizardContext(if (newGradleProjectDto.asModule) project else null, disposable).apply {
        projectJdk = newGradleProjectDto.sdkObject?.let {
          @Suppress("TestOnlyProblems")
          SetupProjectSdkUtil.setupOrDetectSdk(it.sdkName, it.sdkType, it.sdkPath.toString())
        } ?: ProjectRootManager.getInstance(project).projectSdk
      }

      val moduleBuilder = LanguageGeneratorNewProjectWizard.EP_NAME.getIterable().first().let {
        val adapter = BaseLanguageGeneratorNewProjectWizard(wizardContext, it!!)
        TemplatesGroup(object : GeneratorNewProjectWizardBuilderAdapter(adapter) {}).moduleBuilder
      }
      withContext(Dispatchers.EDT) {
        val newProject = if (newGradleProjectDto.asModule) project
        else getNewProject(moduleBuilder!!, newGradleProjectDto.projectName, projectPath, wizardContext)

        val bridgeStep = moduleBuilder!!.getCustomOptionsStep(wizardContext, disposable)
        // Call setupUI method for init lateinit vars
        bridgeStep?.validate()

        (bridgeStep as NewProjectWizardStep).apply {
          baseData?.name = newGradleProjectDto.projectName
          baseData?.path = projectPath.parent.toString()
          javaGradleData?.parentData = parentModule?.let {
            ProjectDataManager.getInstance()
              .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
              .firstOrNull()
              ?.getExternalProjectStructure()
              ?.data
          }
          javaGradleData?.gradleDsl = GradleNewProjectWizardStep.GradleDsl.valueOf(newGradleProjectDto.gradleDSL)
          javaBuildSystemData?.buildSystem = "Gradle"
        }

        moduleBuilder.commit(newProject, modulesConfigurator?.moduleModel, modulesConfigurator ?: ModulesProvider.EMPTY_MODULES_PROVIDER)

        if (!newGradleProjectDto.asModule) runNewProject(projectPath, newProject, project, context)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  override fun getName(): String {
    return NAME
  }
}