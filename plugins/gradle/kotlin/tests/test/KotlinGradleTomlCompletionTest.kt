// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.kotlin.tests

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.idea.completion.api.DependencyArtifactCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionResult
import org.jetbrains.idea.completion.api.DependencyCompletionService
import org.jetbrains.idea.completion.api.DependencyGroupCompletionRequest
import org.jetbrains.idea.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.kotlin.idea.codeInsight.gradle.completion.AbstractKotlinGradleCompletionTest
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.params.ParameterizedTest

private var libraries = "[libraries]" + System.lineSeparator()
private const val tomlPath = "gradle/libs.versions.toml"

@GradleProjectTestApplication
internal class KotlinGradleTomlCompletionTest : AbstractKotlinGradleCompletionTest() {
  @ParameterizedTest
  @BaseGradleVersionSource(
    """
            my-lib.module = "<caret>",
            my-lib = { module = "<caret>"<comma> version = "1" }
        """
  )
  fun `test module completion`(gradleVersion: GradleVersion, completionEscaped: String) {
    val textBefore = libraries + completionEscaped.unescape()
    val textAfter = textBefore.replace("<caret>", "org.example.p:my-long-artifact-id")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
        return flowOf(
          DependencyCompletionResult("org.example.p", "my-long-artifact-id", "2.7.0"),
          DependencyCompletionResult("org.example.p", "my-long-artifact-id-2", "2.7.1"),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit(tomlPath, textBefore)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.lookup.currentItem =
          codeInsightFixture.lookupElements!!.find {
            it.lookupString.contains("org.example.p:my-long-artifact-id")
          }
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult(textAfter)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource(
    """
            my-lib = { group = "<caret>" },
            my-lib = { group = "<caret>"<comma> name = ""<comma> version = "1" }
        """
  )
  fun `test group completion`(gradleVersion: GradleVersion, completionEscaped: String) {
    val textBefore = libraries + completionEscaped.unescape()
    val textAfter = textBefore.replace("<caret>", "org.example.p")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<String> {
        return flowOf("org.example.p", "org.example.p2")
      }
    }, testRootDisposable)
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit(tomlPath, textBefore)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.lookup.currentItem =
          codeInsightFixture.lookupElements!!.find {
            it.lookupString.contains("org.example.p")
          }
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult(textAfter)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource(
    """
            my-lib = { name = "<caret>" },
            my-lib = { group = ""<comma> name = "<caret>"<comma> version = "1" }
        """
  )
  fun `test artifact completion`(gradleVersion: GradleVersion, completionEscaped: String) {
    val textBefore = libraries + completionEscaped.unescape()
    val textAfter = textBefore.replace("<caret>", "org.example.p")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<String> {
        return flowOf("org.example.p", "org.example.p2")
      }
    }, testRootDisposable)
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit(tomlPath, textBefore)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.lookup.currentItem =
          codeInsightFixture.lookupElements!!.find {
            it.lookupString.contains("org.example.p")
          }
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult(textAfter)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource(
    """
            my-lib = { group = "g"<comma> name = "a"<comma> version = "<caret>" }
        """
  )
  fun `test version completion`(gradleVersion: GradleVersion, completionEscaped: String) {
    val textBefore = libraries + completionEscaped.unescape()
    val textAfter = textBefore.replace("<caret>", "org.example.p")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<String> {
        return flowOf("org.example.p", "org.example.p2")
      }
    }, testRootDisposable)
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit(tomlPath, textBefore)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.lookup.currentItem =
          codeInsightFixture.lookupElements!!.find {
            it.lookupString.contains("org.example.p")
          }
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult(textAfter)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource(
    """
            my-lib = "<caret>",
            my-lib-module = "<caret>"
        """
  )
  fun `test coordinates completion`(gradleVersion: GradleVersion, completionEscaped: String) {
    val textBefore = libraries + completionEscaped.unescape()
    val textAfter = textBefore.replace("<caret>", "org.example.p:my-long-artifact-id:2.7.0")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
        return flowOf(
          DependencyCompletionResult("org.example.p", "my-long-artifact-id", "2.7.0"),
          DependencyCompletionResult("org.example.p", "my-long-artifact-id-2", "2.7.1"),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit(tomlPath, textBefore)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.lookup.currentItem =
          codeInsightFixture.lookupElements!!.find {
            it.lookupString.contains("org.example.p:my-long-artifact-id:2.7.0")
          }
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult(textAfter)
      }
    }
  }

  private fun String.unescape(): String = this
    .replace("<colon>", ":")
    .replace("<comma>", ",")
}
