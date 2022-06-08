// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.findOrCreateFile
import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.externalSystem.util.text
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class GradleTestCase : GradleBaseTestCase() {

  fun isGradleAtLeast(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion >= GradleVersion.version(version)

  fun isGradleOlderThan(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion < GradleVersion.version(version)

  fun findOrCreateFile(relativePath: String, text: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      gradleFixture.fileFixture.root.findOrCreateFile(relativePath)
        .also { it.text = text }
    }
  }

  companion object {
    @JvmStatic
    fun createEmptyGradleTestFixture(gradleVersion: GradleVersion): GradleTestFixture {
      val projectName = "empty-project"
      val fixtureFactory = GradleTestFixtureFactory.getFixtureFactory()
      return fixtureFactory.createGradleTestFixture(projectName, gradleVersion) {
        withSettingsFile {
          setProjectName(projectName)
        }
      }
    }

    @JvmStatic
    fun createGradleTestFixture(gradleVersion: GradleVersion, pluginName: String): GradleTestFixture {
      val projectName = "$pluginName-plugin-project"
      val fixtureFactory = GradleTestFixtureFactory.getFixtureFactory()
      return fixtureFactory.createGradleTestFixture(projectName, gradleVersion) {
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