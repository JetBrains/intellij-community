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
internal class GitLabCiIncludeFusCollectorImplicitLocalOrRemoteCasesTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class ImplicitLocalOrRemoteNoComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - subdir/.gitlab-ci.yml
        - https://example.com/.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalysis().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = false)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ImplicitLocalOrRemoteOddComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - subdir/**/.gitlab-ci.yml
        - https://$MY_CUSTOM_DOMAIN/.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalysis().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = true, hasSingleAsterisk = false, hasDoubleAsterisk = true)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ImplicitLocalOrRemoteEvenComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - subdir/*/.gitlab-ci.yml
        - https://example.com/.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalysis().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = false, hasSingleAsterisk = true, hasDoubleAsterisk = false)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ImplicitLocalOrRemoteAllComplications {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - subdir/*/.gitlab-ci.yml
        - subdir/**/.gitlab-ci.yml
        - https://$MY_CUSTOM_DOMAIN/.gitlab-ci.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalysis().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = true, hasSingleAsterisk = true, hasDoubleAsterisk = true)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class ImplicitRemoteIgnoreWronglyUsedAsterisks {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - https://example.com/.gitlab-ci.yml
      # note: asterisks are not applicable to implicit remote include type
        - https://example.com/*.yml
        - https://example.com/**.yml
      build:
        script: echo "Building"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val stats = GitLabCiIncludeApplicationMetricsCollector().performAnalysis().includeStats
        assertExplicitLocalNotFound(stats)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = false)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }
}
