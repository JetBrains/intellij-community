// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.compose.ide.plugin.resources.COMMON_MAIN
import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import org.junit.runners.Parameterized.Parameters
import kotlin.test.assertIs as kAssertIs
import kotlin.test.assertNotNull as kAssertNotNull

class ComposeResourcesCompletionContributorTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test drawable completion filters non-resource declarations`() {
    val files = importProjectFromTestData()

    timeoutRunBlocking(context = Dispatchers.EDT) {
      files.openTestDataFile(sourceSetName)

      codeInsightTestFixture.editor.caretModel.moveToOffset(
        codeInsightTestFixture.file.text.indexOf(DRAWABLE_RESOURCE_PREFIX) + DRAWABLE_RESOURCE_PREFIX.length
      )
      val results = codeInsightTestFixture.completeBasic() ?: emptyArray()
      val lookupStrings = codeInsightTestFixture.lookupElementStrings ?: emptyList()

      assertContainsElements(lookupStrings, listOf("compose_multiplatform", "test"))
      assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))
      results.forEach { result ->
        val renderer = result.expensiveRenderer
        assertNotNull("Expected expensiveRenderer for uncached drawable", renderer)

        val presentation = LookupElementPresentation()
        kAssertIs<LookupElementRenderer<LookupElement>>(renderer)
        renderer.renderElement(result, presentation)
        val icon = presentation.icon
        kAssertNotNull(icon, "Expected slow-rendered icon for ${result.lookupString}")
        assertTrue("Icon width should be > 0", icon.iconWidth > 0)
        assertTrue("Icon height should be > 0", icon.iconHeight > 0)
      }
    }
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test string completion passes through without icon decoration`() {
    val files = importProjectFromTestData()

    timeoutRunBlocking(context = Dispatchers.EDT) {
      files.openTestDataFile(sourceSetName)

      codeInsightTestFixture.editor.caretModel.moveToOffset(
        codeInsightTestFixture.file.text.indexOf(STRING_RESOURCE_PREFIX) + STRING_RESOURCE_PREFIX.length
      )
      val results = codeInsightTestFixture.completeBasic() ?: emptyArray()
      val lookupStrings = codeInsightTestFixture.lookupElementStrings ?: emptyList()

      assertContainsElements(lookupStrings, listOf("test"))
      assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))

      results.filter { it.lookupString == "test" }.forEach { result ->
        assertNull("String resources should not have expensiveRenderer (no icon decoration)", result.expensiveRenderer)
      }
    }
  }

  private fun List<VirtualFile>.openTestDataFile(sourceSetName: String) =
    codeInsightTestFixture.openFileInEditor(
      first { it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/completion.$sourceSetName.kt") }
    )

  private companion object {
    private const val STRING_RESOURCE_PREFIX = "Res.string."
    private const val DRAWABLE_RESOURCE_PREFIX = "Res.drawable."

    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameters(name = "{index}: source set {1} with Gradle-{0}")
    // For testing other source sets, publicResClass needs to be set to true
    fun data(): Collection<Any> = listOf(arrayOf(TARGET_GRADLE_VERSION, COMMON_MAIN))
  }
}