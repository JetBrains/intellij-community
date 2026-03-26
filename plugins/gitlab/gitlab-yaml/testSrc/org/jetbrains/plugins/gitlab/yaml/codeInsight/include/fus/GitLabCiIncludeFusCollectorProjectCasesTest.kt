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
internal class GitLabCiIncludeFusCollectorProjectCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class ProjectNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - project: 'company_name/project_name'
          file: '/.gitlab-ci.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectFound(actualStats = stats,
                         hasRules = false,
                         hasEnvVar = false,
                         hasRef = false,
                         hasSingleAsterisk = false,
                         hasDoubleAsterisk = false)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ProjectOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - project: 'company_name/project_name'
          file:
            - '/.gitlab-ci.yml'
            - '/templates/*/.gitlab-ci-template.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
          ref: branch-name
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectFound(actualStats = stats,
                         hasRules = true,
                         hasEnvVar = false,
                         hasRef = true,
                         hasSingleAsterisk = true,
                         hasDoubleAsterisk = false)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ProjectEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - project: 'company_name/$MY_PROJECT_NAME'
          file: '/templates/**/.gitlab-ci.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectFound(actualStats = stats,
                         hasRules = false,
                         hasEnvVar = true,
                         hasRef = false,
                         hasSingleAsterisk = false,
                         hasDoubleAsterisk = true)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ProjectAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - project: 'company_name/project_name'
          file: '/.gitlab-ci.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
        - project: 'company_name/$MY_PROJECT_NAME'
          file:
            - '/templates/*/.gitlab-ci-template.yml'
            - '/templates/**/.gitlab-ci.yml'
          ref: branch-name
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectFound(actualStats = stats,
                         hasRules = true,
                         hasEnvVar = true,
                         hasRef = true,
                         hasSingleAsterisk = true,
                         hasDoubleAsterisk = true)
      assertUnknownNotFound(stats)
    }
  }
}
