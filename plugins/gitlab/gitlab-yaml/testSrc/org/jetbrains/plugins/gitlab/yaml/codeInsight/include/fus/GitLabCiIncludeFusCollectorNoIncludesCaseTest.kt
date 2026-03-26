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
internal class GitLabCiIncludeFusCollectorNoIncludesCaseTest {
  val projectPath = tempPathFixture()
  val project = projectFixture(projectPath, openAfterCreation = true)
  val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
  val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

  @Nested
  inner class NoIncludesUsed {
    @Suppress("unused")
    val virtualFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
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
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }
}

