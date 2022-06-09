// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.builders

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class SingleGradleSettingsFileFixtureBuilder(
  final override val projectName: String
) : GradleTestFixtureBuilder {

  abstract fun configureSettingsFile(builder: GradleSettingScriptBuilder)

  final override fun createFixture(gradleVersion: GradleVersion): GradleTestFixture {
    val fixtureFactory = GradleTestFixtureFactory.getFixtureFactory()
    return fixtureFactory.createGradleTestFixture(projectName, gradleVersion) {
      withSettingsFile {
        setProjectName(projectName)
        configureSettingsFile(this)
      }
    }
  }

  companion object {
    fun create(projectName: String, configure: GradleSettingScriptBuilder.() -> Unit): GradleTestFixtureBuilder {
      return object : SingleGradleSettingsFileFixtureBuilder(projectName) {
        override fun configureSettingsFile(builder: GradleSettingScriptBuilder) {
          builder.configure()
        }
      }
    }
  }
}