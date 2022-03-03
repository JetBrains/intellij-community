// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.ide.impl.NewProjectUtil
import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.buildSystem
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.language
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.Step
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.UIBundle
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.ProjectInfoBuilder
import org.jetbrains.plugins.gradle.util.ProjectInfoBuilder.ModuleInfo
import org.jetbrains.plugins.gradle.util.ProjectInfoBuilder.ProjectInfo
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.artifactId
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.groupId
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.parentData
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.useKotlinDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.version
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.isSupported
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.jetbrains.plugins.gradle.util.waitForProjectReload
import java.io.File
import com.intellij.openapi.externalSystem.util.use as utilUse


abstract class GradleCreateProjectTestCase : UsefulTestCase() {

  private val bareFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()
  private val tempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
  private val sdkFixture = IdeaTestFixtureFactory.getFixtureFactory().createSdkFixture()

  override fun setUp() {
    super.setUp()
    tempDirFixture.setUp()
    bareFixture.setUp()
    sdkFixture.setUp()
    sdkFixture.setUpSdk(JavaSdk.getInstance()) {
      isSupported(GradleVersion.current(), it)
    }
  }

  override fun tearDown() {
    RunAll(
      { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() },
      { sdkFixture.tearDown() },
      { bareFixture.tearDown() },
      { tempDirFixture.tearDown() },
      { super.tearDown() }
    ).run()
  }

  fun Project.assertProjectStructure(projectInfo: ProjectInfo) {
    ExternalSystemImportingTestCase.assertModules(
      this,
      projectInfo.rootModule.ideName,
      *projectInfo.rootModule.modulesPerSourceSet.toTypedArray(),
      *projectInfo.modules.map { it.ideName }.toTypedArray(),
      *projectInfo.modules.flatMap { it.modulesPerSourceSet }.toTypedArray())
  }

  fun deleteProject(projectInfo: ProjectInfo) {
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        for (module in projectInfo.modules) {
          val root = module.root
          if (root.exists()) {
            root.delete(null)
          }
        }
      }
    }
  }

  fun projectInfo(id: String, useKotlinDsl: Boolean = false, configure: ProjectInfoBuilder.() -> Unit): ProjectInfo {
    val tempDirectory = runReadActionAndWait {
      LocalFileSystem.getInstance()
        .findFileByPath(tempDirFixture.tempDirPath)!!
    }
    return ProjectInfoBuilder.projectInfo(id, tempDirectory) {
      this.useKotlinDsl = useKotlinDsl
      configure()
    }
  }

  protected fun Project.assertDefaultProjectSettings() {
    val externalProjectPath = basePath!!
    val settings = getSettings(this, GradleConstants.SYSTEM_ID) as GradleSettings
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)!!
    assertEquals(projectSettings.externalProjectPath, externalProjectPath)
    assertEquals(projectSettings.isUseQualifiedModuleNames, true)
    assertEquals(settings.storeProjectFilesExternally, true)
  }

  fun withProject(projectInfo: ProjectInfo, save: Boolean = false, action: Project.() -> Unit) {
    createProject(projectInfo).use(save = save) { project ->
      for (moduleInfo in projectInfo.modules) {
        createModule(moduleInfo, project)
      }
      project.action()
    }
  }

  private fun createProject(projectInfo: ProjectInfo): Project {
    return createProject(projectInfo.rootModule.root.path) {
      configureWizardStepSettings(it, projectInfo.rootModule, null)
    }
  }

  private fun createModule(moduleInfo: ModuleInfo, project: Project) {
    val parentData = findProjectData(project, GradleConstants.SYSTEM_ID, project.basePath!!)!!
    return createModule(moduleInfo.root.path, project) {
      configureWizardStepSettings(it, moduleInfo, parentData.data)
    }
  }

  private fun configureWizardStepSettings(step: NewProjectWizardStep, moduleInfo: ModuleInfo, parentData: ProjectData?) {
    step.name = moduleInfo.root.name
    step.path = moduleInfo.root.parent.path
    step.language = "Java"
    step.buildSystem = "Gradle"
    step.useKotlinDsl = moduleInfo.useKotlinDsl
    step.parentData = parentData
    moduleInfo.groupId?.let { step.groupId = it }
    step.artifactId = moduleInfo.artifactId
    moduleInfo.version?.let { step.version = it }
  }

  private fun createProject(directory: String, configure: (NewProjectWizardStep) -> Unit): Project {
    return waitForProjectReload {
      invokeAndWaitIfNeeded {
        val wizard = createWizard(null, directory)
        wizard.runWizard {
          getNewProjectWizardStep(UIBundle.message("label.project.wizard.project.generator.name"))
            ?.also(configure)
        }
        wizard.disposeIfNeeded()
        NewProjectUtil.createFromWizard(wizard, null)
      }
    }
  }


  private fun createModule(directory: String, project: Project, configure: (NewProjectWizardStep) -> Unit) {
    waitForProjectReload {
      ApplicationManager.getApplication().invokeAndWait {
        val wizard = createWizard(project, directory)
        wizard.runWizard {
          getNewProjectWizardStep(UIBundle.message("label.project.wizard.module.generator.name"))
            ?.also(configure)
        }
        wizard.disposeIfNeeded()
        NewModuleAction().createModuleFromWizard(project, null, wizard)
      }
    }
  }

  private fun Step.getNewProjectWizardStep(group: String): NewProjectWizardStep? {
    if (this is ProjectTypeStep) {
      assertTrue(setSelectedTemplate(group, null))
      return customStep as NewProjectWizardStep
    }
    return null
  }

  private fun createWizard(project: Project?, directory: String): AbstractProjectWizard {
    val modulesProvider = project?.let { DefaultModulesProvider(it) } ?: ModulesProvider.EMPTY_MODULES_PROVIDER
    return NewProjectWizard(project, modulesProvider, directory).also {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private fun AbstractProjectWizard.runWizard(configure: Step.() -> Unit) {
    while (true) {
      val currentStep = currentStepObject
      currentStep.configure()
      if (isLast) break
      doNextAction()
      if (currentStep === currentStepObject) {
        throw RuntimeException("$currentStepObject is not validated")
      }
    }
    if (!doFinishAction()) {
      throw RuntimeException("$currentStepObject is not validated")
    }
  }

  fun assertSettingsFileContent(projectInfo: ProjectInfo) {
    val builder = StringBuilder()
    val rootModuleInfo = projectInfo.rootModule
    val useKotlinDsl = rootModuleInfo.useKotlinDsl
    builder.appendLine(defineProject(rootModuleInfo.artifactId, useKotlinDsl))
    for (moduleInfo in projectInfo.modules) {
      val externalName = moduleInfo.externalName
      val artifactId = moduleInfo.artifactId
      when (moduleInfo.isFlat) {
        true -> builder.appendLine(includeFlatModule(externalName, useKotlinDsl))
        else -> builder.appendLine(includeModule(externalName, useKotlinDsl))
      }
      if (externalName != artifactId) {
        builder.appendLine(renameModule(externalName, artifactId, useKotlinDsl))
      }
    }
    val settingsFileName = getSettingsFileName(useKotlinDsl)
    val settingsFile = File(rootModuleInfo.root.path, settingsFileName)
    assertFileContent(settingsFile, builder.toString())
  }

  private fun assertFileContent(file: File, content: String) {
    val actual = convertLineSeparators(file.readText().trim())
    val expected = convertLineSeparators(content.trim())
    assertEquals(expected, actual)
  }

  fun assertBuildScriptFiles(projectInfo: ProjectInfo) {
    for (module in projectInfo.modules + projectInfo.rootModule) {
      val buildFileName = getBuildFileName(module.useKotlinDsl)
      val buildFile = File(module.root.path, buildFileName)
      assertTrue(buildFile.exists())
    }
  }

  private fun getBuildFileName(useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """build.gradle.kts"""
      else -> """build.gradle"""
    }
  }

  private fun getSettingsFileName(useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """settings.gradle.kts"""
      else -> """settings.gradle"""
    }
  }

  private fun defineProject(name: String, useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """rootProject.name = "$name""""
      else -> """rootProject.name = '$name'"""
    }
  }

  private fun includeModule(name: String, useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """include("$name")"""
      else -> """include '$name'"""
    }
  }

  private fun includeFlatModule(name: String, useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """includeFlat("$name")"""
      else -> """includeFlat '$name'"""
    }
  }

  private fun renameModule(from: String, to: String, useKotlinDsl: Boolean): String {
    return when (useKotlinDsl) {
      true -> """findProject(":$from")?.name = "$to""""
      else -> """findProject(':$from')?.name = '$to'"""
    }
  }

  fun Project.use(save: Boolean = false, action: (Project) -> Unit) = utilUse(save, action)
}