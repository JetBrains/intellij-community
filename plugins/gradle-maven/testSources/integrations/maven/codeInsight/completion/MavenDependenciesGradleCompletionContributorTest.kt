// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies that [MavenDependenciesGradleCompletionContributor] does not suppress other
 * completion contributors (via stopHere) for string literals inside a dependencies block
 * that are not maven coordinates, such as project(':path') arguments.
 *
 * The plugin descriptor registering the contributor is not loaded in this test environment,
 * so the contributor is registered manually with order="first", as in production, followed
 * by a marker contributor standing in for any later contributor.
 */
class MavenDependenciesGradleCompletionContributorTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    val ep = ExtensionPointName<CompletionContributorEP>("com.intellij.completion.contributor")
    val descriptor = DefaultPluginDescriptor("MavenDependenciesGradleCompletionContributorTest")
    val maven = CompletionContributorEP(
      "Groovy", MavenDependenciesGradleCompletionContributor::class.java.name, descriptor
    )
    val marker = CompletionContributorEP("Groovy", MarkerContributor::class.java.name, descriptor)
    ep.point.registerExtension(maven, LoadingOrder.FIRST, testRootDisposable)
    ep.point.registerExtension(marker, testRootDisposable)
  }

  fun `test project path argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation project(':<caret>')
      }
      """.trimIndent()
    )
  }

  fun `test parenthesized project path argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation(project(':<caret>'))
      }
      """.trimIndent()
    )
  }

  fun `test project path named argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation project(path: ':<caret>')
      }
      """.trimIndent()
    )
  }

  fun `test files argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation files('libs/<caret>')
      }
      """.trimIndent()
    )
  }

  fun `test file argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation files(file('libs/<caret>'))
      }
      """.trimIndent()
    )
  }

  fun `test qualified files argument is not claimed as a dependency coordinate`() {
    doTestMarkerPresent(
      """
      dependencies {
        implementation rootProject.files('libs/<caret>')
      }
      """.trimIndent()
    )
  }

  fun `test map dependency notation is still claimed`() {
    myFixture.configureByText(
      "build.gradle",
      """
      dependencies {
        implementation group: 'junit', name: 'junit', classifier: '<caret>'
      }
      """.trimIndent()
    )
    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()
    assertDoesntContain(lookups, MARKER_1, MARKER_2)
  }

  private fun doTestMarkerPresent(text: String) {
    myFixture.configureByText("build.gradle", text)
    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()
    assertContainsElements(lookups, MARKER_1, MARKER_2)
  }

  internal class MarkerContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      // Match any prefix, and add two elements so completion shows a popup
      // instead of auto-inserting a sole match
      val anyPrefix = result.withPrefixMatcher("")
      anyPrefix.addElement(LookupElementBuilder.create(MARKER_1))
      anyPrefix.addElement(LookupElementBuilder.create(MARKER_2))
    }
  }

  companion object {
    private const val MARKER_1 = "marker.lookup.element.1"
    private const val MARKER_2 = "marker.lookup.element.2"
  }
}
