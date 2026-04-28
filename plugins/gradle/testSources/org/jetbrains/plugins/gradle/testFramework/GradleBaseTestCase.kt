// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import java.nio.file.Path

@TestApplication
abstract class GradleBaseTestCase {

  val gradleVersion: GradleVersion = GradleVersion.current()

  private val testPathFixture = tempPathFixture()
  val testPath: Path by testPathFixture
  val testRoot: VirtualFile get() = testPath.refreshAndGetVirtualDirectory()

  private val gradleFixture by gradleFixture(gradleVersion)
  val gradleJvm: String get() = gradleFixture.gradleJvm
  val gradleJvmInfo: JdkVersionInfo get() = gradleFixture.gradleJvmInfo

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project {
    return gradleFixture.openProject(testPath.resolve(relativePath), numProjectSyncs)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleFixture.linkProject(project, testPath.resolve(relativePath))
  }

  suspend fun syncProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleFixture.syncProject(project, testPath.resolve(relativePath), configure)
  }

  suspend fun <R> withAllowedProjectSyncs(numProjectSyncs: Int = 1, action: suspend () -> R): R {
    return gradleFixture.withAllowedProjectSyncs(numProjectSyncs, action)
  }
}