// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.gradle.codeInsight.kotlin.testFramework.configureGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
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
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
class KotlinGradleDependenciesAutoPopupTest(gradleVersion: GradleVersion) {

  private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

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

    // Longer than the 3 characters the auto-popup threshold requires: an auto-popup whose only item is exactly
    // the typed prefix is not shown, so the suggestion has to stay distinguishable from what the test types.
    override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.0.0", LOCAL)))
  }

  private lateinit var autoPopupTester: CompletionAutoPopupTester

  @BeforeEach
  fun setUp() {
    autoPopupTester = CompletionAutoPopupTester(codeInsightFixture)
    application.replaceService(DependencyCompletionService::class.java, testCompletionService, codeInsightFixture.testRootDisposable)
  }

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  private fun runTest(test: () -> Unit) {
    autoPopupTester.runWithAutoPopupEnabled { test() }
  }

  private fun testAutoPopupAfterCompletion(
    fileContent: String,
    itemToComplete: String,
    assertion: () -> Unit,
  ) = runTest {
    codeInsightFixture.configureGradleScript(fileContent)
    prepareForCompletion()
    runInEdtAndWait {
      val lookupElements = codeInsightFixture.completeBasic()
      assertNotNull(lookupElements) { "Autocompletion was not expected: fixture.completeBasic() returned null" }
      val expectedElement = lookupElements.find { it.lookupString == itemToComplete }
      assertNotNull(expectedElement) { "`$itemToComplete` should be suggested. " +
                                       "\nActual lookup: ${codeInsightFixture.lookupElementStrings}}"
      }
      codeInsightFixture.lookup.currentItem = expectedElement
      codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
      assertTrue(codeInsightFixture.file.text != fileContent.replace("<caret>", "")) {
        "File should be changed after completion"
      }
    }
    autoPopupTester.joinAutopopup()
    assertion()
  }

  private fun prepareForCompletion() {
    val fixture = codeInsightFixture as CodeInsightTestFixtureImpl
    // Allow a code analyzer to restart during highlighting. The fixture rejects this restart by default.
    fixture.canChangeDocumentDuringHighlighting(true)
    try {
      // Wait for initial analysis after file configuration. This prevents completion from racing with the test setup.
      fixture.doHighlighting()
    }
    finally {
      fixture.canChangeDocumentDuringHighlighting(false)
    }
  }

  /**
   * [typeWithPauses] does not test completion confidence
   */
  private fun CompletionAutoPopupTester.typeFast(text: String) {
    codeInsightFixture.type(text)
    this.joinAutopopup()
    this.joinCompletion()
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyGAV() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(<caret>)
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed in the dependency GAV (IDEA-390474)" }
    autoPopupTester.typeFast("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters are typed in the dependency GAV" }
    assertEquals("myGroup:myArtifact:1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInTopLevelDependency() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          <caret>
      }
    """.trimIndent())
    autoPopupTester.typeFast("my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed for a top-level dependency (IDEA-390474)" }
    autoPopupTester.typeFast("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters for a top-level dependency" }
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyNamedGroup() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(group = <caret>, name = "myArtifact")
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyPositionalGroup() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(<caret>, "myArtifact")
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyNamedName() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(group = "myGroup", name = <caret>)
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("A")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's name argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyPositionalName() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup", <caret>)
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("A")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's name argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyNamedVersion() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(group = "myGroup", name = "myArtifact", version = <caret>)
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"1.")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("0")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's version argument" }
    assertEquals("1.0.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInDependencyPositionalVersion() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup", "myArtifact", <caret>)
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"1.")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("0")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of dependency's version argument" }
    assertEquals("1.0.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInExcludeNamedGroup() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(group = <caret>)
          }
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInExcludePositionalGroup() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(<caret>)
          }
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInExcludeNamedModule() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(group = "myGroup", module = <caret>)
          }
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("A")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of exclude's module argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupUntilThreeCharsInExcludePositionalModule() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude("myGroup", <caret>)
          }
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("A")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of exclude's module argument" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testNoAutoPopupOnClosingQuoteInDependencyGAV() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myArtifact:1.0<caret>")
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered when typing the closing quote" }
  }

  @Test
  fun testNoAutoPopupOnQuoteOutsideDependenciesBlock() = runTest {
    codeInsightFixture.configureGradleScript("""
      tasks {
          register("myTask") {
              val x = <caret>
          }
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered outside of dependencies block" }
  }

  @Test
  fun `test auto popup stays in dependency GAV`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(<caret>)
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"myG")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of dependency's GAV argument" }
    assertEquals("myGroup:myArtifact:1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test auto popup stays in exclude positional group`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation("myGroup:myArtifact:1.0") {
              exclude(<caret>)
          }
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"myG")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of exclude's group argument" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun testAutoPopupAfterCompletingDependencyConfiguration() =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              impl<caret>
          }
        """.trimIndent(),
      itemToComplete = "implementation"
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a dependency configuration"
      }
    }

  @Test
  fun testAutoPopupAfterCompletingDependencyConfigurationWithoutAccessorClass() =
    testAutoPopupAfterCompletion(
      fileContent = """
          val customSourceSet by sourceSets.creating {}
          customSourceSet
          dependencies {
              customSourceSetImpl<caret>
          }
        """.trimIndent(),
      itemToComplete = "customSourceSetImplementation"
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a dependency configuration without accessor class (in quotes)"
      }
    }

  @Test
  fun testAutoPopupAfterCompletingDependencyReturningMethodWithArgs() =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              implementation(p<caret>)
          }
        """.trimIndent(),
      itemToComplete = "project"
    ) {
      assertNotNull(autoPopupTester.lookup) {
        "Auto popup should be triggered after completing a Dependency-returning method with arguments"
      }
    }

  @Test
  fun testNoAutoPopupAfterCompletingDependencyReturningMethodWithoutArgs() =
    testAutoPopupAfterCompletion(
      fileContent = """
          dependencies {
              implementation(gradle<caret>)
          }
        """.trimIndent(),
      itemToComplete = "gradleApi"
    ) {
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered after completing a Dependency-returning method without arguments"
      }
    }

  @Test
  fun `test no auto popup until three chars in kotlin shortcut module positional`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin(<caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"st")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("d")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside kotlin() module argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup until three chars in kotlin shortcut module named`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin(module = <caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"st")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("d")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside kotlin(module = ...) argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup until three chars in kotlin shortcut version positional`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin("stdlib", <caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"1.")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("0")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside kotlin() version argument" }
    assertEquals("1.0.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup until three chars in kotlin shortcut version named`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin(module = "stdlib", version = <caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"1.")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("0")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside kotlin(version = ...) argument" }
    assertEquals("1.0.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup in kotlin shortcut version when module has version`() =
    runTest {
      codeInsightFixture.configureGradleScript("""
        dependencies {
            implementation(kotlin("stdlib:1.0", <caret>))
        }
      """.trimIndent())
      // 3 characters, so the auto-popup threshold is not what suppresses the lookup here
      autoPopupTester.typeWithPauses("\"1.0")
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered for kotlin() version argument when module already has version"
      }
    }

  @Test
  fun `test no auto popup until three chars in embeddedKotlin shortcut module positional`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(embeddedKotlin(<caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"st")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("d")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside embeddedKotlin() module argument" }
    // `embeddedKotlin` accepts no version argument, so the lookup string must contain only the module name.
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup until three chars in embeddedKotlin shortcut module named`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(embeddedKotlin(module = <caret>))
      }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"st")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed" }
    autoPopupTester.typeWithPauses("d")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside embeddedKotlin(module = ...) argument" }
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test no auto popup in embeddedKotlin shortcut second positional argument`() =
    runTest {
      codeInsightFixture.configureGradleScript("""
        dependencies {
            implementation(embeddedKotlin("stdlib", <caret>))
        }
      """.trimIndent())
      // 3 characters, so the auto-popup threshold is not what suppresses the lookup here
      autoPopupTester.typeWithPauses("\"1.0")
      assertNull(autoPopupTester.lookup) {
        "Auto popup should not be triggered for embeddedKotlin() second positional argument because it does not accept a version"
      }
    }

  @Test
  fun `test no auto popup in embedded kotlin shortcut named version`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(embeddedKotlin(module = "stdlib", version = <caret>))
      }
    """.trimIndent())
    // 3 characters, so the auto-popup threshold is not what suppresses the lookup here
    autoPopupTester.typeWithPauses("\"1.0")
    assertNull(autoPopupTester.lookup) {
      "Auto popup should not be triggered for embeddedKotlin() version argument because it does not accept a version"
    }
  }

  @Test
  fun `test auto popup stays in kotlin shortcut module`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin(<caret>))
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"std")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of kotlin's module argument" }
    assertEquals("stdlib:2.0.21", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test auto popup stays in kotlin shortcut version`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(kotlin("stdlib", <caret>))
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"1.0")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of kotlin's version argument" }
    assertEquals("1.0.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @Test
  fun `test auto popup stays in embeddedKotlin shortcut module`() = runTest {
    codeInsightFixture.configureGradleScript("""
      dependencies {
          implementation(embeddedKotlin(<caret>))
      }
    """.trimIndent())
    autoPopupTester.typeFast("\"std")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered and stay inside of embeddedKotlin's module argument" }
    assertEquals("stdlib", autoPopupTester.lookup?.currentItem?.lookupString)
  }
}
