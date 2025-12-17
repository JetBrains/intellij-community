// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.openapi.Disposable
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.completion.api.DependencyCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionResult
import org.jetbrains.idea.completion.api.GradleDependencyCompletionContext
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndexer
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndexerTestImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path

@TestApplication
class GradleDependencyCompletionContributorTest {
  @TestDisposable private lateinit var disposable: Disposable

  private val eelDescriptor = Path("").getEelDescriptor()

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "group",
    "a",
    "artifact",
  ])
  fun `test search single result`(searchString: String) = runBlocking {
    configureLocalIndex("group", "artifact", "version")

    val expected = listOf(DependencyCompletionResult("group", "artifact", "version"))

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)
    assertEquals(expected, results)
  }

  private fun configureLocalIndex(vararg gav: String) {
    application.replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(eelDescriptor, *gav),
      disposable)
  }
}
