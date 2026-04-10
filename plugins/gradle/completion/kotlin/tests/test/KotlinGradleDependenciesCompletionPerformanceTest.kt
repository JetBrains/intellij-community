// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.gradle.completion.GradleLocalDependencyCompletionContributor
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.replaceService
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertTrue

@PerformanceUnitTest
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
internal class KotlinGradleDependenciesCompletionPerformanceTest : AbstractKotlinGradleCompletionTest() {

  @BeforeEach
  fun `set up local completion`() {
    val gavEntries = (0 until 100).map { i -> "com.example.group$i:artifact$i:1.0.$i" }
    ApplicationManager.getApplication().replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, gavEntries),
      testRootDisposable
    )
    ExtensionTestUtil.maskExtensions(
      DependencyCompletionService.EP_NAME,
      listOf(GradleLocalDependencyCompletionContributor()),
      testRootDisposable
    )
    removeOtherCompletionContributors()
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test dependency completion performance`(gradleVersion: GradleVersion) {
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { implementation(\"<caret>\") }")
      val repeatSize = 10
      invokeAndWaitIfNeeded {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        Benchmark.newBenchmark("KotlinGradleDependenciesCompletion") {
          codeInsightFixture.psiManager.dropResolveCaches()
          repeat(repeatSize) {
            val elements = codeInsightFixture.completeBasic()
            assertTrue(elements != null && elements.isNotEmpty())
          }
        }.start(KotlinGradleDependenciesCompletionPerformanceTest::`test dependency completion performance`)
      }
    }
  }
}
