// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml.tests

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@ParameterizedClass
@BaseGradleVersionSource("true,false")
internal class GradleTomlInsertionTest(
  private val myGradleVersion: GradleVersion,
  private val runInDumbMode: Boolean,
) : GradleCodeInsightTestCase() {
  @TestDisposable
  private lateinit var disposable: Disposable

  private fun testInsertion(
    service: DependencyCompletionService,
    before: String,
    expectedAfter: String,
    lookupString: String,
  ) {
    testEmptyProject(myGradleVersion) {
      ApplicationManager.getApplication().replaceService(DependencyCompletionService::class.java, service, disposable)
      checkCaret(before)
      writeTextAndCommit("gradle/libs.versions.toml", before)
      val completeAndCheck = {
        runInEdtAndWait {
          codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
          // completeBasic() returns null when only one item is available and it is auto-inserted
          val elements = codeInsightFixture.completeBasic()
          if (elements != null) {
            val item = elements.find { it.lookupString == lookupString }
                       ?: error("Lookup element '$lookupString' not found. Available: ${elements.map { it.lookupString }}")
            codeInsightFixture.lookup.currentItem = item
            codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
          }
          codeInsightFixture.checkResult(expectedAfter)
        }
      }
      if (runInDumbMode) {
        DumbModeTestUtils.runInDumbModeSynchronously(project) { completeAndCheck() }
      }
      else {
        completeAndCheck()
      }
    }
  }

  @Test
  fun `group completion replaces full string content`() {
    testInsertion(
      service = object : DependencyCompletionService {
        override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
          flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.jetbrains.kotlin", SERVER)))
      },
      before = """
        [libraries]
        my-lib = { group = "org.<caret>.something-else", name = "kotlin-stdlib" }
      """.trimIndent(),
      expectedAfter = """
        [libraries]
        my-lib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib" }
      """.trimIndent(),
      lookupString = "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `artifact completion replaces full string content`() {
    testInsertion(
      service = object : DependencyCompletionService {
        override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
          flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-stdlib", SERVER)))
      },
      before = """
        [libraries]
        my-lib = { group = "org.jetbrains.kotlin", name = "kotlin-<caret>-something-else" }
      """.trimIndent(),
      expectedAfter = """
        [libraries]
        my-lib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib" }
      """.trimIndent(),
      lookupString = "kotlin-stdlib",
    )
  }

  @Test
  fun `version completion replaces full string content`() {
    testInsertion(
      service = object : DependencyCompletionService {
        override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
          flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("2.1.0", SERVER)))
      },
      before = """
        [libraries]
        my-lib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.<caret>something.else" }
      """.trimIndent(),
      expectedAfter = """
        [libraries]
        my-lib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.1.0" }
      """.trimIndent(),
      lookupString = "2.1.0",
    )
  }

  @Test
  fun `module completion replaces full string content`() {
    testInsertion(
      service = object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
          flowOf(DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.jetbrains.kotlin",
            "kotlin-stdlib",
            "2.1.0",
            source = SERVER
          )))
      },
      before = """
        [libraries]
        my-lib.module = "org.jetbrains.kotlin:kotlin-<caret>-something-else"
      """.trimIndent(),
      expectedAfter = """
        [libraries]
        my-lib.module = "org.jetbrains.kotlin:kotlin-stdlib"
      """.trimIndent(),
      lookupString = "org.jetbrains.kotlin:kotlin-stdlib",
    )
  }

  @Test
  fun `GAV completion replaces full string content`() {
    testInsertion(
      service = object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
          flowOf(DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.jetbrains.kotlin",
            "kotlin-stdlib",
            "2.1.0",
            source = SERVER
          )))
      },
      before = """
        [libraries]
        my-lib = "org.jetbrains.kotlin:kotlin-<caret>-something-else"
      """.trimIndent(),
      expectedAfter = """
        [libraries]
        my-lib = "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"
      """.trimIndent(),
      lookupString = "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
    )
  }
}
