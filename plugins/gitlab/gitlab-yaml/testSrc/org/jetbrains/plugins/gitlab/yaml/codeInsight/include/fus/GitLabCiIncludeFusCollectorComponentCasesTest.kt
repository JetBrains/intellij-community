// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class GitLabCiIncludeFusCollectorComponentCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class ComponentNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - component: https://gitlab.com/my-org/some-component-name@1.0
        - component: https://gitlab.com/another-org/another-component@2.1.3
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentFound(hasRules = false, hasEnvVar = false)
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ComponentOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - component: https://gitlab.com/my-org/some-component-name@1.0
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentFound(hasRules = true, hasEnvVar = false)
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ComponentEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - component: $CI_SERVER_FQDN/my-org/some-component-name@1.0
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentFound(hasRules = false, hasEnvVar = true)
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ComponentAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - component: https://gitlab.com/my-org/some-component-name@1.0
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
        - component: $CI_SERVER_FQDN/my-org/some-component-name@1.0
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentFound(hasRules = true, hasEnvVar = true)
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }
}
