// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.params.ParameterizedTest

private var libraries = "[libraries]\n"
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
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id",
            "2.7.0",
            source = LOCAL
          )),
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id-2",
            "2.7.1",
            source = LOCAL
          )),
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
      override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p2", source = LOCAL))
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
      override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p2", source = LOCAL))
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
      override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.example.p2", source = LOCAL))
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
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id",
            "2.7.0",
            source = LOCAL
          )),
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id-2",
            "2.7.1",
            source = LOCAL
          )),
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
