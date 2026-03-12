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
internal class GitLabCiIncludeFusCollectorExplicitRemoteCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class ExplicitRemoteNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - remote: 'https://example.com/.gitlab-ci.yml'
        - remote: 'https://gitlab.com/templates/.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteFound(hasRules = false, hasEnvVar = false, hasCache = false)
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitRemoteOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - remote: 'https://example.com/.gitlab-ci.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
          cache:
            key: project-cache
            paths:
              - .cache/
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteFound(hasRules = true, hasEnvVar = false, hasCache = true)
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitRemoteEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - remote: 'https://$MY_CUSTOM_DOMAIN/some_remote_file.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteFound(hasRules = false, hasEnvVar = true, hasCache = false)
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }

  @Nested
  inner class ExplicitRemoteAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - remote: 'https://example.com/.gitlab-ci.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
        - remote: 'https://$MY_CUSTOM_DOMAIN/some_remote_file.yml'
          cache:
            key: project-cache
            paths:
              - .cache/
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalyzing().includeStats
      stats.assertExplicitLocalNotFound()
      stats.assertExplicitRemoteFound(hasRules = true, hasEnvVar = true, hasCache = true)
      stats.assertImplicitLocalOrRemoteNotFound()
      stats.assertTemplateNotFound()
      stats.assertComponentNotFound()
      stats.assertProjectNotFound()
      stats.assertUnknownNotFound()
    }
  }
}
