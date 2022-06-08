// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.findOrCreateFile
import com.intellij.openapi.externalSystem.util.runWriteAction
import com.intellij.openapi.externalSystem.util.text
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.util.ThrowableRunnable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.junit.Assume
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class GradleLightTestCase : UsefulTestCase() {

  @Parameterized.Parameter
  lateinit var gradleVersion: String

  @Suppress("LeakingThis")
  private val versionMatcherRule = asOuterRule(VersionMatcherRule())

  private lateinit var fixture: GradleTestFixture

  val projectFixture: IdeaProjectTestFixture
    get() = object : IdeaProjectTestFixture {
      override fun getProject(): Project = fixture.project
      override fun getModule(): Module = fixture.module
      override fun setUp() {}
      override fun tearDown() {}
    }

  open fun createGradleTestFixture(gradleVersion: GradleVersion): GradleTestFixture =
    createEmptyGradleTestFixture(gradleVersion)

  fun reloadProject() =
    fixture.reloadProject()

  fun isGradleAtLeast(version: String): Boolean =
    fixture.gradleVersion.baseVersion >= GradleVersion.version(version)

  fun isGradleOlderThan(version: String): Boolean =
    fixture.gradleVersion.baseVersion < GradleVersion.version(version)

  override fun setUp() {
    Assume.assumeThat(gradleVersion, versionMatcherRule.matcher)
    fixture = createGradleTestFixture(GradleVersion.version(gradleVersion))
    fixture.setUp()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { fixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  fun findOrCreateFile(relativePath: String, text: String): VirtualFile {
    fixture.fileFixture.snapshot(relativePath)
    return runWriteAction {
      fixture.fileFixture.root.findOrCreateFile(relativePath)
        .also { it.text = text }
    }
  }

  companion object {
    @JvmStatic
    @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
    @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
    open fun data(): Collection<Array<Any>> = GradleImportingTestCase.data()

    fun createEmptyGradleTestFixture(gradleVersion: GradleVersion): GradleTestFixture {
      val projectName = "empty-project"
      return GradleTestFixtureFactory.getFixtureFactory()
        .createGradleTestFixture(projectName, gradleVersion) {
          withSettingsFile {
            setProjectName(projectName)
          }
        }
    }

    @JvmStatic
    fun createGradleTestFixture(gradleVersion: GradleVersion, pluginName: String): GradleTestFixture {
      val projectName = "$pluginName-plugin-project"
      return GradleTestFixtureFactory.getFixtureFactory()
        .createGradleTestFixture(projectName, gradleVersion) {
          withSettingsFile {
            setProjectName(projectName)
          }
          withBuildFile(gradleVersion) {
            when (pluginName) {
              "java" -> withJavaPlugin()
              "idea" -> withIdeaPlugin()
              "groovy" -> withGroovyPlugin()
              else -> withPlugin(pluginName)
            }
          }
        }
    }
  }
}