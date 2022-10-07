// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

interface GradleTestFixtureBuilder {

  val projectName: String

  fun createFixture(gradleVersion: GradleVersion): GradleProjectTestFixture

  companion object {
    val EMPTY_PROJECT: GradleTestFixtureBuilder = empty()
    val JAVA_PROJECT: GradleTestFixtureBuilder = plugin("java")
    val GROOVY_PROJECT: GradleTestFixtureBuilder = plugin("groovy")
    val IDEA_PLUGIN_PROJECT: GradleTestFixtureBuilder = plugin("idea")

    fun create(projectName: String, configure: FileTestFixture.Builder.(GradleVersion) -> Unit): GradleTestFixtureBuilder {
      return object : GradleTestFixtureBuilder {
        override val projectName: String = projectName
        override fun createFixture(gradleVersion: GradleVersion): GradleProjectTestFixture {
          val fixtureFactory = GradleTestFixtureFactory.getFixtureFactory()
          return fixtureFactory.createGradleProjectTestFixture(projectName, gradleVersion) {
            configure(gradleVersion)
          }
        }
      }
    }

    fun empty(): GradleTestFixtureBuilder {
      return create("empty-project") {
        withSettingsFile {
          setProjectName("empty-project")
        }
      }
    }

    fun buildFile(projectName: String, configure: TestGradleBuildScriptBuilder.(GradleVersion) -> Unit): GradleTestFixtureBuilder {
      return create(projectName) { gradleVersion ->
        withSettingsFile {
          setProjectName(projectName)
        }
        withBuildFile(gradleVersion) {
          configure(gradleVersion)
        }
      }
    }

    fun settingsFile(projectName: String, configure: GradleSettingScriptBuilder.(GradleVersion) -> Unit): GradleTestFixtureBuilder {
      return create(projectName) { gradleVersion ->
        withSettingsFile {
          setProjectName(projectName)
          configure(gradleVersion)
        }
      }
    }

    fun plugin(pluginName: String): GradleTestFixtureBuilder {
      return buildFile("$pluginName-plugin-project") {
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