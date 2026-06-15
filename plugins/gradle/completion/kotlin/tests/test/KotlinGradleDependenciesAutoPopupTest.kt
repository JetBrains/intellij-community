// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.Disposable
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradleDependenciesAutoPopupTest : K2GradleCodeInsightTestCase() {

  private val testCompletionService = object : DependencyCompletionService {
    override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
      flowOf(
        DependencyCompletionEvent.Item(DependencyCompletionResult("myGroup", "myArtifact", "1.0", source = LOCAL)),
        DependencyCompletionEvent.Item(DependencyCompletionResult("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21", source = LOCAL)),
      )

    override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("myGroup", LOCAL)))

    override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(
        DependencyCompletionEvent.Item(DependencyPartCompletionResult("myArtifact", LOCAL)),
        DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-stdlib", LOCAL)),
      )

    override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.0", LOCAL)))
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private var _autoPopupTester: CompletionAutoPopupTester? = null
  private val autoPopupTester: CompletionAutoPopupTester
    get() = _autoPopupTester ?: error("autoPopupTester is not initialized")

  override fun setUp() {
    super.setUp()
    _autoPopupTester = CompletionAutoPopupTester(codeInsightFixture)
    application.replaceService(DependencyCompletionService::class.java, testCompletionService, disposable)
  }

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    test(gradleVersion, GRADLE_KTS_JAVA_PLUGIN_FIXTURE) { autoPopupTester.runWithAutoPopupEnabled { test() } }
  }

  private fun testAutoPopupAfterCompletion(
    fileContent: String,
    itemToComplete: String,
    gradleVersion: GradleVersion,
    assertion: () -> Unit,
  ) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", fileContent)
    fixture.configureFromExistingVirtualFile(file)
    // make sure that IDEA is ready for completion
    fixture.doHighlighting()
    runInEdtAndWait {
      val lookupElements = fixture.completeBasic()
      assertNotNull(lookupElements) { "Autocompletion was not expected: fixture.completeBasic() returned null" }
      val expectedElement = lookupElements.find { it.lookupString == itemToComplete }
      assertNotNull(expectedElement) { "`$itemToComplete` should be suggested. " +
                                       "\nActual lookup: ${fixture.lookupElementStrings}}" }
      fixture.lookup.currentItem = expectedElement
      fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
      assertTrue(fixture.file.text != fileContent.replace("<caret>", "")) {
        "File should be changed after completion"
      }
    }
    autoPopupTester.joinAutopopup()
    assertion()
  }

  /**
   * [typeWithPauses] does not test completion confidence
   */
  private fun CompletionAutoPopupTester.typeFast(text: String) {
    codeInsightFixture.type(text)
    this.joinAutopopup()
    this.joinCompletion()
  }

  companion object {
    private val GRADLE_KTS_JAVA_PLUGIN_FIXTURE = GradleTestFixtureBuilder
      .create("KotlinGradleDependenciesAutoPopupHandlerTest") { gradleVersion ->
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
          withJavaPlugin()
          withPrefix { code("val customSourceSet by sourceSets.creating {}") }
        }
      }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupUntilThreeCharsInDependencyGAV(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(<caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed in the dependency GAV (IDEA-390474)" }
    autoPopupTester.typeFast("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters are typed in the dependency GAV" }
    assertEquals("myGroup:myArtifact:1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupUntilThreeCharsInTopLevelDependency(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          <caret>
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed for a top-level dependency (IDEA-390474)" }
    autoPopupTester.typeFast("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters for a top-level dependency" }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyNamedGroup(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(group = <caret>, name = "myArtifact")
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyPositionalGroup(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(<caret>, "myArtifact")
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyNamedName(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(group = "myGroup", name = <caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's name argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyPositionalName(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup", <caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's name argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyNamedVersion(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(group = "myGroup", name = "myArtifact", version = <caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's version argument" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInDependencyPositionalVersion(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup", "myArtifact", <caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of dependency's version argument" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInExcludeNamedGroup(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(group = <caret>)
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInExcludePositionalGroup(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(<caret>)
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInExcludeNamedModule(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(group = "myGroup", module = <caret>)
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of exclude's module argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInExcludePositionalModule(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude("myGroup", <caret>)
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of exclude's module argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupOnClosingQuoteInDependencyGAV(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myArtifact:1.0<caret>")
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered when typing the closing quote" }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupOnQuoteOutsideDependenciesBlock(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      tasks {
          register("myTask") {
              val x = <caret>
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered outside of dependencies block" }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup stays in dependency GAV`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(<caret>)
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"myG")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of dependency's GAV argument" }
    assertEquals("myGroup:myArtifact:1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup stays in exclude positional group`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(<caret>)
          }
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"myG")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupAfterCompletingDependencyConfiguration(gradleVersion: GradleVersion) =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              impl<caret>
          }
        """.trimIndent(),
      itemToComplete = "implementation",
      gradleVersion
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a dependency configuration"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupAfterCompletingDependencyConfigurationWithoutAccessorClass(gradleVersion: GradleVersion) =
    testAutoPopupAfterCompletion(
      fileContent = """
          val customSourceSet by sourceSets.creating {}
          customSourceSet
          dependencies {
              customSourceSetImpl<caret>
          }
        """.trimIndent(),
      itemToComplete = "customSourceSetImplementation",
      gradleVersion
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a dependency configuration without accessor class (in quotes)"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupAfterCompletingDependencyReturningMethodWithArgs(gradleVersion: GradleVersion) =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              implementation(p<caret>)
          }
        """.trimIndent(),
      itemToComplete = "project",
      gradleVersion
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a Dependency-returning method with arguments"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupAfterCompletingDependencyReturningMethodWithoutArgs(gradleVersion: GradleVersion) =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              implementation(gradle<caret>)
          }
        """.trimIndent(),
      itemToComplete = "gradleApi",
      gradleVersion
    ) {
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered after completing a Dependency-returning method without arguments"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in kotlin shortcut module positional`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin(<caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside kotlin() module argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in kotlin shortcut module named`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin(module = <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside kotlin(module = ...) argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in kotlin shortcut version positional`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin("stdlib", <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside kotlin() version argument" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in kotlin shortcut version named`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin(module = "stdlib", version = <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside kotlin(version = ...) argument" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test no auto popup on quote in kotlin shortcut version when module has version`(gradleVersion: GradleVersion) =
    runTest(gradleVersion) {
      val file = writeTextAndCommit("build.gradle.kts", """
        dependencies {
            implementation(kotlin("stdlib:1.0", <caret>))
        }
      """.trimIndent())
      codeInsightFixture.configureFromExistingVirtualFile(file)
      autoPopupTester.typeWithPauses("\"")
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered for kotlin() version argument when module already has version"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in embeddedKotlin shortcut module positional`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(embeddedKotlin(<caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside embeddedKotlin() module argument" }
    // `embeddedKotlin` accepts no version argument, so the lookup string must contain only the module name.
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup on quote in embeddedKotlin shortcut module named`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(embeddedKotlin(module = <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside embeddedKotlin(module = ...) argument" }
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test no auto popup on quote in embeddedKotlin shortcut second positional argument`(gradleVersion: GradleVersion) =
    runTest(gradleVersion) {
      val file = writeTextAndCommit("build.gradle.kts", """
        dependencies {
            implementation(embeddedKotlin("stdlib", <caret>))
        }
      """.trimIndent())
      codeInsightFixture.configureFromExistingVirtualFile(file)
      autoPopupTester.typeWithPauses("\"")
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered for embeddedKotlin() second positional argument because it does not accept a version"
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test no auto popup on quote in embedded kotlin shortcut named version`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(embeddedKotlin(module = "stdlib", version = <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) {
      "Auto popup should not be triggered for embeddedKotlin() version argument because it does not accept a version"
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup stays in kotlin shortcut module`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin(<caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"std")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of kotlin's module argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup stays in kotlin shortcut version`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(kotlin("stdlib", <caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"1")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of kotlin's version argument" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test auto popup stays in embeddedKotlin shortcut module`(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    val file = writeTextAndCommit("build.gradle.kts", """
      dependencies {
          implementation(embeddedKotlin(<caret>))
      }
    """.trimIndent())
    codeInsightFixture.configureFromExistingVirtualFile(file)
    autoPopupTester.typeFast("\"std")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of embeddedKotlin's module argument" }
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }
}