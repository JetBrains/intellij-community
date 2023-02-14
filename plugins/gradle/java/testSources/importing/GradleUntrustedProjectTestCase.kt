// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.application.subscribe
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.getDirectory
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.openProjectAsyncAndWait
import org.jetbrains.plugins.gradle.util.awaitProjectReload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

@TestApplication
abstract class GradleUntrustedProjectTestCase {

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile

  private lateinit var gradleVersion: GradleVersion
  private lateinit var gradleJvmFixture: SdkTestFixture

  private lateinit var trustedLocations: List<Path>

  private val rootPath: Path get() = testRoot.toNioPath()

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = fileFixture.tempDirPath.toNioPath().refreshAndGetVirtualDirectory()

    gradleVersion = GradleVersion.current()
    gradleJvmFixture = GradleTestFixtureFactory.getFixtureFactory()
      .createGradleJvmTestFixture(gradleVersion)
    gradleJvmFixture.setUp()

    val trustedLocations = ArrayList<Path>()
    this.trustedLocations = trustedLocations
    TrustedProjectsListener.TOPIC.subscribe(testDisposable, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) {
        trustedLocations.addAll(locatedProject.projectRoots)
      }
    })
  }

  @AfterEach
  fun tearDown() {
    runAll(
      { gradleJvmFixture.tearDown() },
      { fileFixture.tearDown() }
    )
  }

  suspend fun initGradleProject(relativePath: String) {
    writeAction {
      val projectRoot = testRoot.findOrCreateDirectory(relativePath)
      projectRoot.createSettingsFile {
        setProjectName(projectRoot.name)
      }
    }
  }

  suspend fun openProjectAsyncAndWait(relativePath: String): Project {
    val projectRoot = writeAction {
      testRoot.getDirectory(relativePath)
    }
    return openProjectAsyncAndWait(projectRoot)
  }

  suspend fun linkProjectAsyncAndWait(project: Project, relativePath: String) {
    val externalProjectPath = rootPath.getResolvedPath(relativePath).toCanonicalPath()
    awaitProjectReload {
      linkAndRefreshGradleProject(externalProjectPath, project)
    }
  }

  fun assertTrustedLocations(vararg relativePaths: String) {
    assertTrustedLocations(relativePaths.toList())
  }

  fun assertTrustedLocations(relativePaths: List<String>) {
    Assertions.assertEquals(
      relativePaths.map { rootPath.getResolvedPath(it) }.toSet(),
      trustedLocations.toSet()
    )
  }

  fun assertProjectLocator(
    project: Project,
    vararg relativePaths: String
  ) {
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    Assertions.assertEquals(
      relativePaths.map { rootPath.getResolvedPath(it) }.toSet(),
      locatedProject.projectRoots.toSet()
    )
  }
}