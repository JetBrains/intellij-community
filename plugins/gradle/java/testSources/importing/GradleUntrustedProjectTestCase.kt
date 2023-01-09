// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.application.subscribe
import com.intellij.ide.impl.trustedProjects.LocatedProject
import com.intellij.ide.impl.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.file.VirtualFileUtil
import com.intellij.openapi.file.VirtualFileUtil.getAbsoluteNioPath
import com.intellij.openapi.file.VirtualFileUtil.getAbsolutePath
import com.intellij.openapi.file.system.LocalFileSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.openProjectAsyncAndWait
import org.jetbrains.plugins.gradle.util.getProjectDataLoadPromise
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

@TestApplication
abstract class GradleUntrustedProjectTestCase {

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile

  private lateinit var gradleVersion: GradleVersion
  private lateinit var gradleJvmFixture: SdkTestFixture

  private lateinit var trustedLocations: List<Path>

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = LocalFileSystemUtil.getDirectory(fileFixture.tempDirPath)

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
    val projectRoot = writeAction {
      VirtualFileUtil.findOrCreateDirectory(testRoot, relativePath)
    }
    projectRoot.createSettingsFile {
      setProjectName(projectRoot.name)
    }
  }

  suspend fun openProjectAsyncAndWait(relativePath: String): Project {
    val projectRoot = writeAction {
      VirtualFileUtil.getDirectory(testRoot, relativePath)
    }
    return openProjectAsyncAndWait(projectRoot)
  }

  suspend fun linkProjectAsyncAndWait(project: Project, relativePath: String) {
    val deferred = getProjectDataLoadPromise()
    linkAndRefreshGradleProject(testRoot.getAbsolutePath(relativePath), project)
    withContext(Dispatchers.EDT) {
      withTimeout(10.minutes) {
        deferred.asDeferred().join()
      }
    }
  }

  fun assertTrustedLocations(vararg relativePaths: String) {
    assertTrustedLocations(relativePaths.toList())
  }

  fun assertTrustedLocations(relativePaths: List<String>) {
    Assertions.assertEquals(
      relativePaths.map { testRoot.getAbsoluteNioPath(it) }.toSet(),
      trustedLocations.toSet()
    )
  }

  fun assertProjectLocator(
    project: Project,
    vararg relativePaths: String
  ) {
    val locatedProject = LocatedProject.locateProject(project)
    Assertions.assertEquals(
      relativePaths.map { testRoot.getAbsoluteNioPath(it) }.toSet(),
      locatedProject.projectRoots.toSet()
    )
  }
}