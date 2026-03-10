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
internal class GitLabCiIncludeFusCollectorLocalIncludeCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class ExplicitLocalNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - local: '/templates/.gitlab-ci-template.yml'
        - local: '/.gitlab-ci-template.yml'
        - local: '.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalFound(hasRules = false, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = false)
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitLocalOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/templates/.gitlab-ci-template.yml'
          rules:
            - if: $CI_COMMIT_BRANCH == "main"
        - local: '/templates/*/.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalFound(hasRules = true, hasEnvVar = false, hasSingleAsterisk = true, hasDoubleAsterisk = false)
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitLocalEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/$CUSTOM_PATH/.gitlab-ci-template.yml'
        - local: '/templates/**/.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalFound(hasRules = false, hasEnvVar = true, hasSingleAsterisk = false, hasDoubleAsterisk = true)
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitLocalAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/templates/.gitlab-ci-template.yml'
          rules:
            - if: $CI_COMMIT_BRANCH == "main"
        - local: '/$CUSTOM_PATH/.gitlab-ci-template.yml'
        - local: '/templates/*/.gitlab-ci-template.yml'
        - local: '/templates/**/.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing()
      stats.assertExplicitLocalFound(hasRules = true, hasEnvVar = true, hasSingleAsterisk = true, hasDoubleAsterisk = true)
      stats.assertExplicitRemoteNotFound()
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }
}

