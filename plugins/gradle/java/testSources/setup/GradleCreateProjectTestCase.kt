// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.impl.createProjectFromWizardImpl
import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GENERATE_ONBOARDING_TIPS_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GIT_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GROUP_ID_PROPERTY_NAME
import com.intellij.ide.wizard.Step
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.vfs.getDirectory
import com.intellij.testFramework.withProjectAsync
import com.intellij.ui.UIBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.javaGradleData
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

abstract class GradleCreateProjectTestCase : GradleTestCase() {

  @BeforeEach
  fun cleanupStoredPropertiesInNPW() {
    PropertiesComponent.getInstance().setValue("NewProjectWizard.gradleDslState", null)
    PropertiesComponent.getInstance().setValue(GIT_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(GROUP_ID_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(ADD_SAMPLE_CODE_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(GENERATE_ONBOARDING_TIPS_NAME, null)
  }

  suspend fun createProjectByWizard(projectInfo: ProjectInfo): Project {
    Assertions.assertTrue(projectInfo.composites.isEmpty(), "NPW cannot create composite projects please use initProject instead.")
    val rootModuleInfo = projectInfo.rootModule
    return createProjectByWizard(JAVA) {
      configureWizardStepSettings(this, rootModuleInfo, null)
    }.withProjectAsync { project ->
      val parentPath = testPath.resolve(projectInfo.relativePath).toCanonicalPath()
      val parentData = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, parentPath)!!
      for (moduleInfo in projectInfo.modules) {
        if (moduleInfo != rootModuleInfo) {
          createModuleByWizard(project, JAVA) {
            configureWizardStepSettings(this, moduleInfo, parentData.data)
          }
        }
      }
    }
  }

  private fun configureWizardStepSettings(step: NewProjectWizardStep, moduleInfo: ModuleInfo, parentData: ProjectData?) {
    step.baseData!!.name = moduleInfo.name
    step.baseData!!.path = testPath.resolve(moduleInfo.relativePath).normalize().parent.toCanonicalPath()
    step.javaBuildSystemData!!.buildSystem = GRADLE
    step.javaGradleData!!.gradleDsl = moduleInfo.gradleDsl
    step.javaGradleData!!.parentData = parentData
    step.javaGradleData!!.groupId = moduleInfo.groupId
    step.javaGradleData!!.artifactId = moduleInfo.artifactId
    step.javaGradleData!!.version = moduleInfo.version
    step.javaGradleData!!.addSampleCode = false
  }

  suspend fun createProjectByWizard(
    group: String,
    numProjectSyncs: Int = 1,
    configure: NewProjectWizardStep.() -> Unit,
  ): Project {
    val wizard = createAndConfigureWizard(group = group, project = null, configure = configure)
    return awaitOpenProjectConfiguration(numProjectSyncs) {
      val project = createProjectFromWizardImpl(wizard = wizard, projectFile = Path.of(wizard.newProjectFilePath), projectToClose = null)
      withContext(Dispatchers.EDT) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
      project!!
    }
  }

  suspend fun createModuleByWizard(
    project: Project,
    group: String,
    numProjectSyncs: Int = 1,
    configure: NewProjectWizardStep.() -> Unit,
  ): Module? {
    val wizard = createAndConfigureWizard(group, project, configure)
    return awaitProjectConfiguration(project, numProjectSyncs) {
      blockingContext {
        invokeAndWaitIfNeeded {
          val module = NewModuleAction().createModuleFromWizard(project, null, wizard)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
          module
        }
      }
    }
  }

  private suspend fun createAndConfigureWizard(
    group: String,
    project: Project?,
    configure: NewProjectWizardStep.() -> Unit
  ): AbstractProjectWizard {
    return blockingContext {
      invokeAndWaitIfNeeded {
        val modulesProvider = DefaultModulesProvider.createForProject(project)
        val wizard = NewProjectWizard(project, modulesProvider, null)
        try {
          wizard.runWizard {
            this as ProjectTypeStep
            Assertions.assertTrue(setSelectedTemplate(group, null))
            val step = customStep as NewProjectWizardStep
            step.configure()
          }
        }
        finally {
          Disposer.dispose(wizard.disposable)
        }
        wizard
      }
    }
  }

  private fun AbstractProjectWizard.runWizard(configure: Step.() -> Unit): AbstractProjectWizard {
    while (true) {
      val currentStep = currentStepObject
      currentStep.configure()
      if (isLast) break
      doNextAction()
      require(currentStep !== currentStepObject) {
        "$currentStepObject is not validated"
      }
    }
    require(doFinishAction()) {
      "$currentStepObject is not validated"
    }
    return this
  }

  fun ModuleInfo.Builder.withJavaBuildFile() {
    withBuildFile {
      addGroup(groupId)
      addVersion(version)
      withJavaPlugin()
      withJUnit()
    }
  }

  override fun ModuleInfo.Builder.withBuildFile(configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    filesConfiguration.withBuildFile(gradleVersion, gradleDsl = gradleDsl, configure = configure)
  }

  fun assertBuildFiles(projectInfo: ProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      assertBuildFiles(compositeInfo)
    }
    for (moduleInfo in projectInfo.modules) {
      val moduleRoot = testRoot.getDirectory(moduleInfo.relativePath)
      moduleInfo.filesConfiguration.assertContentsAreEqual(moduleRoot)
    }
  }

  fun assertDaemonJvmProperties(project: Project) {
    val externalProjectPath = Path.of(project.basePath!!)
    val properties = GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)
    Assertions.assertEquals(gradleJvmInfo.toJvmCriteria(), properties.criteria)
  }

  companion object {

    val NEW_EMPTY_PROJECT = UIBundle.message("label.project.wizard.empty.project.generator.name")
  }
}