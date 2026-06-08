// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.gradle.completion.GradleLocalDependencyCompletionContributor
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.replaceService
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.asSafely
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

@PerformanceUnitTest

@GradleProjectTestApplication
internal class KotlinGradleDependenciesCompletionPerformanceTest : AbstractKotlinGradleCompletionTest() {

  @BeforeEach
  fun `set up local completion`() {
    ApplicationManager.getApplication().replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, getGavEntries()),
      testRootDisposable
    )
    ExtensionTestUtil.maskExtensions(
      DependencyCompletionService.EP_NAME,
      listOf(GradleLocalDependencyCompletionContributor()),
      testRootDisposable
    )
  }

  private fun getGavEntries(): List<String> = (0 until 100).map { i -> "com.example.group$i:artifact$i:1.0.$i" }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test completing gav coordinates inside a scope argument`(gradleVersion: GradleVersion) =
    testDependenciesCompletionPerformance(
      input = "implementation(\"<caret>\")",
      expectedElements = getGavEntries(),
      gradleVersion,
    )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test completing a scope`(gradleVersion: GradleVersion) =
    testDependenciesCompletionPerformance(
      input = "i<caret>",
      expectedElements = listOf("implementation", "testImplementation"),
      gradleVersion,
    )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test completing a scope argument without quotes`(gradleVersion: GradleVersion) =
    testDependenciesCompletionPerformance(
      input = "implementation(<caret>)",
      expectedElements = listOf("libs", "libs.my.library.aaa", "libs.bundles.my.bundle.aaa", "platform", "project"),
      gradleVersion,
    )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test completing a Dependency-returning method argument without quotes`(gradleVersion: GradleVersion) =
    testDependenciesCompletionPerformance(
      input = "implementation(platform(<caret>))",
      expectedElements = listOf("libs.my.library.aaa", "libs.bundles.my.bundle.aaa"),
      gradleVersion,
    )

  private fun testDependenciesCompletionPerformance(
    input: String,
    expectedElements: List<String>,
    gradleVersion: GradleVersion,
  ) {
    test(gradleVersion, COMPLETION_FIXTURE) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $input }")
      invokeAndWaitIfNeeded {
        fixture.configureFromExistingVirtualFile(file)
        val repeatSize = 10
        Benchmark
          .newBenchmark("completionPerformance") {
            repeat(repeatSize) {
              val lookup = fixture.complete(CompletionType.BASIC)
              assertLookupIsValid(lookup, expectedElements)
              // Hide the lookup to not affect the next iteration. If it is shown and the completion is called at least twice,
              // the results will include suggestions from the ignored contributors.
              LookupManager.getInstance(project).hideActiveLookup()
            }
          }
          .setup {
            removeRangeMarkers()
          }
          .runAsStressTest()
          .start(getTestMethodFqn())
      }
    }
  }

  private fun getTestMethodFqn(): String =
    testInfo.testClass.get().name + "." + testInfo.testMethod.get().name

  private fun removeRangeMarkers() {
    val documentEx = this.fixture.editor.document.asSafely<DocumentEx>()
    val rangeMarkers = ArrayList<RangeMarker>()
    documentEx?.processRangeMarkers { rangeMarkers.add(it) }
    rangeMarkers.forEach { marker -> documentEx?.removeRangeMarker(marker as RangeMarkerEx) }
  }

  private fun assertLookupIsValid(
    lookup: Array<out LookupElement>?,
    expectedElements: List<String>,
  ) {
    assertNotNull(lookup) {
      "Autocompletion was not expected ('fixture.complete(CompletionType.BASIC)' returned null)"
    }
    val lookupStrings = lookup.map { it.lookupString }
    val absentElements = expectedElements.filter { it !in lookupStrings }
    Assertions.assertTrue(absentElements.isEmpty()) {
      "The completion was expected to contain all of the following elements: \n$expectedElements, " +
      "\nbut these are absent: \n$absentElements"
    }
  }


  companion object {
    private val COMPLETION_FIXTURE =
      GradleTestFixtureBuilder.create("KotlinGradleDependenciesCompletionPerformanceTest") { gradleVersion ->
        withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
          setProjectName("KotlinGradleDependenciesCompletionPerformanceTest")
        }
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
          withKotlinJvmPlugin()
          withMavenCentral()
          withPostfix {
            call("dependencies") {

            }
          }
        }
        withFile(
          "gradle/libs.versions.toml", """
            [versions]
            my-version = "1.0.0"
            [plugins]
            my-plugin = "my.plugin:1.0.0"
            [libraries]
            my-library-aaa = "com.example:my-library-aaa:1.0.0"
            my-library-bbb = "com.example:my-library-bbb:1.0.0"
            [bundles]
            my-bundle-aaa = ["my-library-aaa", "my-library-bbb"]
          """.trimIndent()
        )
      }
  }
}
