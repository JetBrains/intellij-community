// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCommonMainOnly
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs as kAssertIs
import kotlin.test.assertNotNull as kAssertNotNull

@ComposeResourcesCommonMainOnly
class ComposeResourcesCompletionContributorTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test drawable completion filters non-resource declarations`() = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      openTestDataFile()

      appendCompletionExpression(DRAWABLE_COMPLETION_EXPRESSION)

      val results = codeInsightFixture.completeBasic() ?: emptyArray()
      val lookupStrings = codeInsightFixture.lookupElementStrings ?: emptyList()

      assertContainsElements(lookupStrings, listOf("compose_multiplatform", "test"))
      assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))
      results.forEach { result ->
        val renderer = result.expensiveRenderer
        kAssertNotNull(renderer, "Expected expensiveRenderer for uncached drawable")

        val presentation = LookupElementPresentation()
        kAssertIs<LookupElementRenderer<LookupElement>>(renderer)
        renderer.renderElement(result, presentation)
        val icon = presentation.icon
        kAssertNotNull(icon, "Expected slow-rendered icon for ${result.lookupString}")
        assertTrue(icon.iconWidth > 0, "Icon width should be > 0")
        assertTrue(icon.iconHeight > 0, "Icon height should be > 0")
      }
    }
  }

  @Test
  fun `test string completion passes through without icon decoration`() = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      openTestDataFile()

      appendCompletionExpression(STRING_COMPLETION_EXPRESSION)

      val results = codeInsightFixture.completeBasic() ?: emptyArray()
      val lookupStrings = codeInsightFixture.lookupElementStrings ?: emptyList()

      assertContainsElements(lookupStrings, listOf("test"))
      assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))

      results.filter { it.lookupString == "test" }.forEach { result ->
        assertNull(result.expensiveRenderer, "String resources should not have expensiveRenderer (no icon decoration)")
      }
    }
  }

  private fun appendCompletionExpression(expression: String) {
    with(codeInsightFixture.editor) {
      caretModel.moveToOffset(document.textLength)
    }

    codeInsightFixture.type("\n$expression")
  }

  private fun openTestDataFile() {
    codeInsightFixture.configureFromExistingVirtualFile(
      canonicalProjectFile("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
    )
  }

  private companion object {
    private const val STRING_COMPLETION_EXPRESSION = "val stringCompletionTest = Res.string."
    private const val DRAWABLE_COMPLETION_EXPRESSION = "val drawableCompletionTest = Res.drawable."
  }
}
