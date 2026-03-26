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
internal class GitLabCiIncludeFusCollectorTemplateCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class TemplateNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - template: Auto-DevOps.gitlab-ci.yml
        - template: Jobs/Deploy.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateFound(actualStats = stats, hasRules = false, hasEnvVar = false)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class TemplateOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - template: Auto-DevOps.gitlab-ci.yml
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateFound(actualStats = stats, hasRules = true, hasEnvVar = false)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class TemplateEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - template: $MY_CUSTOM_NAME_PREFIX.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateFound(actualStats = stats, hasRules = false, hasEnvVar = true)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class TemplateAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - template: Auto-DevOps.gitlab-ci.yml
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
        - template: $MY_CUSTOM_NAME_PREFIX.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateFound(actualStats = stats, hasRules = true, hasEnvVar = true)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }
}
