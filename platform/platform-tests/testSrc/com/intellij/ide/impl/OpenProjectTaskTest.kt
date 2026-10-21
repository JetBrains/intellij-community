// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.mock.MockProject
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.projectImport.ProjectOpenedCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class OpenProjectTaskTest {
  private val disposable = Disposer.newDisposable()

  @AfterEach
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun `implementation options preserve their payload and file marker`() {
    val originalImplOptions = Any()
    val replacementImplOptions = Any()
    val options = OpenProjectTask {
      implOptions = originalImplOptions
      opensFileAfterProjectOpen = true
      initFrameEarlyForNewProject = true
    }

    assertThat(options.implOptions).isNotSameAs(originalImplOptions)
    assertThat(options.effectiveImplOptions).isSameAs(originalImplOptions)
    assertThat(options.opensFileAfterProjectOpen).isTrue()
    assertThat(options.initFrameEarlyForNewProject).isTrue()

    val derivedOptions = OpenProjectTask(options) { projectName = "derived" }
    assertThat(derivedOptions.effectiveImplOptions).isSameAs(originalImplOptions)
    assertThat(derivedOptions.opensFileAfterProjectOpen).isTrue()
    assertThat(derivedOptions.initFrameEarlyForNewProject).isTrue()

    val replacedOptions = derivedOptions.withImplOptions(replacementImplOptions)
    assertThat(replacedOptions.effectiveImplOptions).isSameAs(replacementImplOptions)
    assertThat(replacedOptions.opensFileAfterProjectOpen).isTrue()
    assertThat(replacedOptions.initFrameEarlyForNewProject).isTrue()

    val defaultOptions = OpenProjectTask {}
    assertThat(defaultOptions.implOptions).isNotNull()
    assertThat(defaultOptions.effectiveImplOptions).isNull()
    assertThat(defaultOptions.opensFileAfterProjectOpen).isFalse()
    assertThat(defaultOptions.initFrameEarlyForNewProject).isFalse()
  }

  @Test
  fun `a derived task starts from every option of the base`() {
    val project = MockProject(null, disposable)
    val projectToClose = MockProject(null, disposable)
    val callback = ProjectOpenedCallback { _, _ -> }
    val beforeInitTask: (Project) -> Unit = {}
    val beforeOpenTask: suspend (Project) -> Boolean = { true }
    val preparedToOpenTask: suspend (Module) -> Unit = {}
    val processorChooser: (List<Any>) -> Any = { it.first() }
    val implOptions = Any()
    val rootDir = Path.of("root")
    val base = OpenProjectTask {
      projectName = "base"
      forceOpenInNewFrame = true
      forceReuseFrame = true
      isNewProject = true
      // differs from `isNewProject`, so a recomputed default would show
      useDefaultProjectAsTemplate = false
      runConfigurators = true
      preloadServices = false
      isProjectCreatedWithWizard = true
      runConversionBeforeOpen = false
      preventIprLookup = true
      this.projectToClose = projectToClose
      beforeInitTasks = listOf(beforeInitTask)
      beforeOpenTasks = listOf(beforeOpenTask)
      preparedToOpen = preparedToOpenTask
      this.callback = callback
      showWelcomeScreen = false
      projectWorkspaceId = "workspace"
      projectFrameTypeId = "frame"
      this.implOptions = implOptions
      opensFileAfterProjectOpen = true
      initFrameEarlyForNewProject = true
      line = 3
      column = 4
      projectRootDir = rootDir
      this.processorChooser = processorChooser
      this.project = project
    }

    val derived = OpenProjectTask(base) {}

    assertThat(derived.projectName).isEqualTo("base")
    assertThat(derived.forceOpenInNewFrame).isTrue()
    assertThat(derived.forceReuseFrame).isTrue()
    assertThat(derived.isNewProject).isTrue()
    assertThat(derived.useDefaultProjectAsTemplate).isFalse()
    assertThat(derived.runConfigurators).isTrue()
    assertThat(derived.preloadServices).isFalse()
    assertThat(derived.isProjectCreatedWithWizard).isTrue()
    assertThat(derived.runConversionBeforeOpen).isFalse()
    assertThat(derived.preventIprLookup).isTrue()
    assertThat(derived.projectToClose).isSameAs(projectToClose)
    assertThat(derived.beforeInitTasks).containsExactly(beforeInitTask)
    assertThat(derived.beforeOpenTasks).containsExactly(beforeOpenTask)
    assertThat(derived.preparedToOpen).isNotNull()
    assertThat(derived.callback).isSameAs(callback)
    assertThat(derived.showWelcomeScreen).isFalse()
    assertThat(derived.projectWorkspaceId).isEqualTo("workspace")
    assertThat(derived.projectFrameTypeId).isEqualTo("frame")
    assertThat(derived.effectiveImplOptions).isSameAs(implOptions)
    assertThat(derived.opensFileAfterProjectOpen).isTrue()
    assertThat(derived.initFrameEarlyForNewProject).isTrue()
    assertThat(derived.line).isEqualTo(3)
    assertThat(derived.column).isEqualTo(4)
    assertThat(derived.projectRootDir).isEqualTo(rootDir)
    assertThat(derived.processorChooser).isSameAs(processorChooser)
    assertThat(derived.project).isSameAs(project)
    assertThat(derived.createModule).isFalse()
  }

  @Test
  fun `a derived task keeps createModule of a base without a project`() {
    assertThat(OpenProjectTask(OpenProjectTask { createModule = false }) {}.createModule).isFalse()
    assertThat(OpenProjectTask(OpenProjectTask {}) {}.createModule).isTrue()
    assertThat(OpenProjectTask(OpenProjectTask {}) {}.preparedToOpen).isNull()
  }

  @Test
  fun `a derivation applies its changes and keeps the other options`() {
    var isBaseTaskInvoked = false
    val base = OpenProjectTask {
      projectName = "base"
      runConfigurators = false
      beforeInitTasks += { isBaseTaskInvoked = true }
    }

    var isAddedTaskInvoked = false
    val derived = OpenProjectTask(base) {
      projectName = "derived"
      beforeInitTasks += { isAddedTaskInvoked = true }
    }

    assertThat(derived.projectName).isEqualTo("derived")
    assertThat(derived.runConfigurators).isFalse()
    derived.beforeInit!!.invoke(MockProject(null, disposable))
    assertThat(isBaseTaskInvoked).isTrue()
    assertThat(isAddedTaskInvoked).isTrue()
    assertThat(base.projectName).isEqualTo("base")
    assertThat(base.beforeInitTasks).hasSize(1)
  }
}
