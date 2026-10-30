// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Tests what a project that the welcome screen loaded without opening may do.
 *
 * The welcome screen loads a recent project to run a VCS action on it. Such a project is never open and never initialized. The VCS code
 * tells it apart by its [ProjectFrameCapability.NO_FRAME_OF_ITS_OWN] capability, not by these states. A project with a frame has them
 * too while it opens or closes.
 */
@TestApplication
internal class ProjectVcsReadinessTest {
  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "false")
  fun `an open project tracks its changes with the registry key off`() {
    assertTrue(openProject().tracksChanges)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "false")
  fun `a loaded project tracks no changes with the registry key off`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertFalse(loadedProject().tracksChanges)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `a loaded project tracks its changes with the registry key on`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertTrue(loadedProject().tracksChanges)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `a project with a frame tracks no changes while it is not open`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable)
    assertFalse(loadedProject().tracksChanges)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `a loaded project is ready for the changes view`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertTrue(loadedProject().isReadyForChangesView)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "false")
  fun `a loaded project is not ready for the changes view with the registry key off`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertFalse(loadedProject().isReadyForChangesView)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `a disposed project is not ready for the changes view`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertFalse(loadedProject(isDisposed = true).isReadyForChangesView)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `an open project that is still coming up is not ready for the changes view`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable)
    assertFalse(openProject(isInitialized = false).isReadyForChangesView)
  }

  @Test
  @RegistryKey(key = RECENT_PROJECT_COMMIT, value = "true")
  fun `a project with a frame is not ready for the changes view while it is not open`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable)
    assertFalse(loadedProject().isReadyForChangesView)
  }

  @Test
  fun `an initialized project runs code analysis`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable)
    assertTrue(openProject().isReadyForCodeAnalysis)
  }

  @Test
  fun `a project that suppresses indexing runs no code analysis`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
    assertFalse(openProject().isReadyForCodeAnalysis)
  }

  @Test
  fun `a project that suppresses background activities runs no code analysis`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
    assertFalse(openProject().isReadyForCodeAnalysis)
  }

  /** A check commits the documents synchronously, which needs an initialized project. The indexes alone are not enough. */
  @Test
  fun `a loaded project runs no code analysis even when it builds indexes`(@TestDisposable disposable: Disposable) {
    useCapabilities(disposable, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
    assertFalse(loadedProject().isReadyForCodeAnalysis)
  }

  private fun useCapabilities(disposable: Disposable, vararg capabilities: ProjectFrameCapability) {
    ExtensionTestUtil.maskExtensions(ProjectFrameCapabilitiesService.EP_NAME, listOf(FixedCapabilities(capabilities.toSet())), disposable)
  }

  /** A project open in a frame. [Project.isInitialized] is false only while it is still coming up. */
  private fun openProject(isInitialized: Boolean = true): Project = project(isOpen = true, isInitialized = isInitialized)

  /**
   * A project that is not open and not initialized.
   *
   * The capabilities tell what it is. With [ProjectFrameCapability.NO_FRAME_OF_ITS_OWN] it is a recent project the welcome screen loaded.
   * Without it, it is a project with a frame that opens or closes.
   */
  private fun loadedProject(isDisposed: Boolean = false): Project =
    project(isOpen = false, isInitialized = false, isDisposed = isDisposed)

  private fun project(isOpen: Boolean, isInitialized: Boolean, isDisposed: Boolean = false): Project =
    mock<Project>().also {
      `when`(it.isOpen).thenReturn(isOpen)
      `when`(it.isInitialized).thenReturn(isInitialized)
      `when`(it.isDisposed).thenReturn(isDisposed)
    }
}

/** Answers with the capabilities the test asks for, whatever the project. */
private class FixedCapabilities(private val capabilities: Set<ProjectFrameCapability>) : ProjectFrameCapabilitiesProvider {
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> = capabilities

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
}
