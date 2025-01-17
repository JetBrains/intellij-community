// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.Companion.IDEA_MODEL_FQN
import org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.Companion.IDEA_MODULE_FQN
import org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.Companion.IDEA_MODULE_IML_FQN
import org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.Companion.IDEA_PROJECT_FQN
import org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.Companion.IDE_XML_MERGER_FQN
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradleIdeaPluginTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test idea closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, IDEA_PLUGIN_PROJECT) {
      testBuildscript(decorator, "idea { <caret> }") {
        closureDelegateTest(IDEA_MODEL_FQN, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test idea project closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, IDEA_PLUGIN_PROJECT) {
      testBuildscript(decorator, "idea { project { <caret> } }") {
        closureDelegateTest(IDEA_PROJECT_FQN, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test idea project ipr closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, IDEA_PLUGIN_PROJECT) {
      testBuildscript(decorator, "idea { project { ipr { <caret> } } }") {
        closureDelegateTest(IDE_XML_MERGER_FQN, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test idea module closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, IDEA_PLUGIN_PROJECT) {
      testBuildscript(decorator, "idea { module { <caret> } }") {
        closureDelegateTest(IDEA_MODULE_FQN, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test idea module iml closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, IDEA_PLUGIN_PROJECT) {
      testBuildscript(decorator, "idea { module { iml { <caret> } } }") {
        closureDelegateTest(IDEA_MODULE_IML_FQN, 1)
      }
    }
  }

  companion object {

    private val IDEA_PLUGIN_PROJECT = GradleTestFixtureBuilder.create("GradleIdeaPluginTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradleIdeaPluginTest")
      }
      withBuildFile(gradleVersion) {
        withIdeaPlugin()
      }
    }
  }
}