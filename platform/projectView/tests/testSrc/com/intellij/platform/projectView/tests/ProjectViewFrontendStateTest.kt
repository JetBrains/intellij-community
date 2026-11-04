// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.impl.ProjectViewFrontendState
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.application.UI
import com.intellij.platform.projectView.actions.legacyProjectViewOption
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class ProjectViewFrontendStateTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  private lateinit var savedFrontendOptions: ProjectViewFrontendState.Options
  private lateinit var savedOptions: ProjectViewFrontendState.Options
  private lateinit var savedDefaultOptions: ProjectViewFrontendState.Options
  private lateinit var savedSharedOptions: ProjectViewFrontendState.Options

  @BeforeEach
  fun saveOptions() {
    savedFrontendOptions = ProjectViewFrontendState.getInstance(project).options.value
    savedOptions = ProjectViewState.getInstance(project).getOptions()
    savedDefaultOptions = ProjectViewState.getDefaultInstance().getOptions()
    savedSharedOptions = ProjectViewSharedSettings.instance.getOptions()
  }

  @AfterEach
  fun restoreOptions() {
    ProjectViewFrontendState.getInstance(project).setOptions(savedFrontendOptions)
    ProjectViewState.getInstance(project).setOptions(savedOptions)
    ProjectViewState.getDefaultInstance().setOptions(savedDefaultOptions)
    ProjectViewSharedSettings.instance.setOptions(savedSharedOptions)
  }

  @Test
  fun `a project without a stored state takes the options from ProjectViewState`() {
    ProjectViewState.getInstance(project).setOptions(ALL_SELECTED)

    val frontendState = ProjectViewFrontendState(project)
    frontendState.noStateLoaded()

    assertEquals(ALL_SELECTED, frontendState.options.value)
  }

  @Test
  fun `a loaded state replaces the options`() {
    val options = ProjectViewFrontendState.Options(autoscrollToSource = true, openDirectoriesWithSingleClick = false, autoscrollFromSource = true)
    val savedState = ProjectViewFrontendState(project).apply { setOptions(options) }.state

    val frontendState = ProjectViewFrontendState(project)
    frontendState.loadState(savedState)

    assertEquals(options, frontendState.options.value)
  }

  @Test
  fun `the settings service writes the options to ProjectViewFrontendState and keeps a copy in ProjectViewState`() {
    ProjectViewPaneSettingsService.getInstance(project).setOptionSelected(ProjectViewPaneOption.OpenDirectoriesWithSingleClick, true)

    assertTrue(ProjectViewFrontendState.getInstance(project).openDirectoriesWithSingleClick)
    assertTrue(ProjectViewState.getInstance(project).openDirectoriesWithSingleClick)
    assertTrue(ProjectViewState.getDefaultInstance().openDirectoriesWithSingleClick)
    assertTrue(ProjectViewSharedSettings.instance.openDirectoriesWithSingleClick)
  }

  @Test
  fun `both Project View implementations read the options from ProjectViewFrontendState`(): Unit = timeoutRunBlocking {
    ProjectViewState.getInstance(project).autoscrollFromSource = false
    ProjectViewFrontendState.getInstance(project).autoscrollFromSource = true

    assertTrue(ProjectViewPaneSettingsService.getInstance(project).isOptionSelected(ProjectViewPaneOption.AutoscrollFromSource))
    val isSelectedInLegacyProjectView = withContext(Dispatchers.UI) {
      legacyProjectViewOption(project, ProjectViewPaneOption.AutoscrollFromSource).isSelected
    }
    assertTrue(isSelectedInLegacyProjectView)
    assertFalse(ProjectViewState.getInstance(project).autoscrollFromSource)
  }
}

private val ALL_SELECTED = ProjectViewFrontendState.Options(
  autoscrollToSource = true,
  openDirectoriesWithSingleClick = true,
  autoscrollFromSource = true,
)

private fun ProjectViewFrontendState.setOptions(options: ProjectViewFrontendState.Options) {
  autoscrollToSource = options.autoscrollToSource
  openDirectoriesWithSingleClick = options.openDirectoriesWithSingleClick
  autoscrollFromSource = options.autoscrollFromSource
}

private fun ProjectViewState.getOptions(): ProjectViewFrontendState.Options {
  return ProjectViewFrontendState.Options(autoscrollToSource, openDirectoriesWithSingleClick, autoscrollFromSource)
}

private fun ProjectViewState.setOptions(options: ProjectViewFrontendState.Options) {
  autoscrollToSource = options.autoscrollToSource
  openDirectoriesWithSingleClick = options.openDirectoriesWithSingleClick
  autoscrollFromSource = options.autoscrollFromSource
}

private fun ProjectViewSharedSettings.getOptions(): ProjectViewFrontendState.Options {
  return ProjectViewFrontendState.Options(autoscrollToSource, openDirectoriesWithSingleClick, autoscrollFromSource)
}

private fun ProjectViewSharedSettings.setOptions(options: ProjectViewFrontendState.Options) {
  autoscrollToSource = options.autoscrollToSource
  openDirectoriesWithSingleClick = options.openDirectoriesWithSingleClick
  autoscrollFromSource = options.autoscrollFromSource
}
