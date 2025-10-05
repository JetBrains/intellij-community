// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.impl.createProjectFromWizardImpl
import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GENERATE_ONBOARDING_TIPS_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GIT_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GROUP_ID_PROPERTY_NAME
import com.intellij.ide.wizard.Step
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.getDirectory
import com.intellij.ui.UIBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

@RegistryKey("ide.activity.tracking.enable.debug", "true")
@SystemProperty("intellij.progress.task.ignoreHeadless", "true")
abstract class GradleNewProjectWizardTestCase : GradleTestCase() {

  private lateinit var executionOutputTracker: ExternalSystemExecutionTracer

  @BeforeEach
  fun installGradleExecutionOutputTracker(@TestDisposable disposable: Disposable) {
    executionOutputTracker = ExternalSystemExecutionTracer()
    executionOutputTracker.install(disposable)
  }

  @BeforeEach
  fun cleanupStoredPropertiesInNPW() {
    PropertiesComponent.getInstance().setValue("NewProjectWizard.gradleDslState", null)
    PropertiesComponent.getInstance().setValue(GIT_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(GROUP_ID_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(ADD_SAMPLE_CODE_PROPERTY_NAME, null)
    PropertiesComponent.getInstance().setValue(GENERATE_ONBOARDING_TIPS_NAME, null)
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
      withContext(Dispatchers.EDT) {
        val module = NewModuleAction().createModuleFromWizard(project, null, wizard)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        module
      }
    }
  }

  private suspend fun createAndConfigureWizard(
    group: String,
    project: Project?,
    configure: NewProjectWizardStep.() -> Unit
  ): AbstractProjectWizard {
    return withContext(Dispatchers.EDT) {
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

  override fun assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) {
    for (projectInfo in projectsInfo) {
      assertBuildFiles(projectInfo)
    }
    super.assertProjectState(project, *projectsInfo)
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
    val jvmCriteria = gradleJvmInfo.toJvmCriteria()

    // This assumption contains information about the requested Gradle daemon JVM parameters.
    // Therefore, in the worst scenario we check that we form Gradle request correctly.
    val isDiscoApiAvailable = !executionOutputTracker.output.joinToString("").contains(
      "Toolchain resolvers did not return download URLs providing a JDK matching " +
      "{languageVersion={${jvmCriteria.version}}, vendor=${jvmCriteria.vendor}, implementation=vendor-specific, nativeImageCapable=false} " +
      "for any of the requested platforms"
    )
    Assumptions.assumeTrue(isDiscoApiAvailable) {
      "The https://api.foojay.io/disco/v3.0/distributions service is unavailable."
     }

    val externalProjectPath = Path.of(project.basePath!!)
    val properties = GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)
    Assertions.assertEquals(jvmCriteria, properties.criteria)
  }

  companion object {

    val NEW_EMPTY_PROJECT = UIBundle.message("label.project.wizard.empty.project.generator.name")
  }
}