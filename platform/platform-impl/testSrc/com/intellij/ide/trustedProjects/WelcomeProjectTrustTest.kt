// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedHostsConfigurable
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.application
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The welcome-screen ("Home") project directory is a system path: it is trusted implicitly,
 * its trust state is never persisted, and it never appears in Settings | Trusted Locations (IJPL-254558).
 */
@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
internal class WelcomeProjectTrustTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val tempPath by tempPathFixture()

  private val welcomePath: Path get() = tempPath.resolve("testHome")

  private val trustEvents = mutableListOf<String>()

  @BeforeEach
  fun setUp() {
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<WelcomeScreenProjectProvider>("com.intellij.welcomeScreenProjectProvider"),
      listOf(TestWelcomeScreenProjectProvider(welcomePath)),
      disposable,
    )
    application.messageBus.connect(disposable).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) {
        trustEvents += "trusted: ${locatedProject.projectRoots}"
      }

      override fun onProjectUntrusted(locatedProject: LocatedProject) {
        trustEvents += "untrusted: ${locatedProject.projectRoots}"
      }
    })
  }

  @AfterEach
  fun tearDown() {
    // both stores are application-level and would leak into the next test
    TrustedPaths.getInstance().loadState(TrustedPaths.State())
    TrustedPathsSettings.getInstance().loadState(TrustedPathsSettings.State())
  }

  @Test
  fun `the welcome project path is trusted implicitly`() {
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath))
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath.resolve("file.txt")))
    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(tempPath.resolve("sibling")))

    // the implicit trust leaves no persistent record
    assertTrue(TrustedPaths.getInstance().getExplicitlyTrustedPaths().isEmpty())
  }

  @Test
  fun `the trust state of the welcome project is not persisted and cannot be revoked`() {
    TrustedProjects.setProjectTrusted(welcomePath, false)
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath))

    TrustedProjects.setProjectTrusted(welcomePath, true)
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath))

    assertTrue(TrustedPaths.getInstance().getExplicitlyTrustedPaths().isEmpty())
    assertEquals(emptyList<String>(), trustEvents)
  }

  @Test
  fun `a stale persisted answer for the welcome path is overridden`() {
    val locatedProject = TrustedProjectsLocator.locateProject(welcomePath, project = null)
    TrustedPaths.getInstance().setProjectTrustedState(locatedProject, isTrusted = false)

    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath))
  }

  @Test
  fun `a stale record of the welcome path is hidden and cleaned by the settings page`() {
    val otherPath = tempPath.resolve("project")
    val trustedPaths = TrustedPaths.getInstance()
    trustedPaths.setProjectTrustedState(TrustedProjectsLocator.locateProject(welcomePath, project = null), isTrusted = true)
    trustedPaths.setProjectTrustedState(TrustedProjectsLocator.locateProject(otherPath, project = null), isTrusted = true)
    trustEvents.clear()

    val configurable = TrustedHostsConfigurable()
    assertEquals(listOf(otherPath.toString()), configurable.getMergedTrustedPaths())

    configurable.applyMergedTrustedPaths(listOf(otherPath.toString()))
    assertEquals(listOf(otherPath.toString()), trustedPaths.getExplicitlyTrustedPaths())
    assertEquals(emptyList<String>(), trustEvents)
  }

  @Test
  fun `the trusted parent location of the welcome project stays listed`() {
    // the "trust all projects in the folder" checkbox grants trust to the parent directory
    TrustedPathsSettings.getInstance().setTrustedPaths(listOf(tempPath.toString()))

    val configurable = TrustedHostsConfigurable()
    assertEquals(listOf(tempPath.toString()), configurable.getMergedTrustedPaths())

    configurable.applyMergedTrustedPaths(listOf(tempPath.toString()))
    assertEquals(listOf(tempPath.toString()), TrustedPathsSettings.getInstance().getTrustedPaths())
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(welcomePath))
  }

  private class TestWelcomeScreenProjectProvider(private val path: Path) : WelcomeScreenProjectProvider() {
    override fun getWelcomeScreenProjectPath(): Path = path

    override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean = false

    override fun doIsWelcomeScreenProject(project: Project): Boolean = false

    override fun doIsForceDisabledFileColors(): Boolean = true

    override fun doGetCreateNewFileProjectPrefix(): String = "testProject"
  }
}
