// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.compose.ide.plugin.resources.ANDROID_MAIN
import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ComposeResourcesCompletionContributorTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test drawable completion filters non-resource declarations`() = timeoutRunBlocking(timeout = 3.minutes, context = Dispatchers.EDT) {
    assumeTrue("temporarily disable for androidMain since it's not recognised as source root", sourceSetName != ANDROID_MAIN)
    val files = importProjectFromTestData()
    files.openTestDataFile(sourceSetName)

    val caretTarget = "Res.drawable."
    codeInsightTestFixture.editor.caretModel.moveToOffset(
      codeInsightTestFixture.file.text.indexOf(caretTarget) + caretTarget.length
    )
    val results = codeInsightTestFixture.completeBasic() ?: emptyArray()
    val lookupStrings = codeInsightTestFixture.lookupElementStrings ?: emptyList()

    assertContainsElements(lookupStrings, listOf("compose_multiplatform", "test"))
    assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))
    results.forEach { result ->
      val renderer = result.expensiveRenderer
      assertNotNull("Expected expensiveRenderer for uncached drawable", renderer)

      val presentation = LookupElementPresentation()
      @Suppress("unchecked_cast")
      (renderer as LookupElementRenderer<LookupElement>).renderElement(result, presentation)
      val icon = presentation.icon
      assertNotNull("Expected slow-rendered icon for ${result.lookupString}", icon)
      assertTrue("Icon width should be > 0", icon!!.iconWidth > 0)
      assertTrue("Icon height should be > 0", icon.iconHeight > 0)
    }
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test string completion passes through without icon decoration`() =
    timeoutRunBlocking(timeout = 3.minutes, context = Dispatchers.EDT) {
    assumeTrue("temporarily disable for androidMain since it's not recognised as source root", sourceSetName != ANDROID_MAIN)
    val files = importProjectFromTestData()
    files.openTestDataFile(sourceSetName)

    val caretTarget = "Res.string."
    codeInsightTestFixture.editor.caretModel.moveToOffset(
      codeInsightTestFixture.file.text.indexOf(caretTarget) + caretTarget.length
    )
    val results = codeInsightTestFixture.completeBasic() ?: emptyArray()
    val lookupStrings = codeInsightTestFixture.lookupElementStrings ?: emptyList()

    assertContainsElements(lookupStrings, listOf("test"))
    assertDoesntContain(lookupStrings, listOf("equals", "hashCode", "toString"))

    results.filter { it.lookupString == "test" }.forEach { result ->
      assertNull("String resources should not have expensiveRenderer (no icon decoration)", result.expensiveRenderer)
    }
  }

  private fun List<VirtualFile>.openTestDataFile(sourceSetName: String) =
    codeInsightTestFixture.openFileInEditor(
      first { it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/completion.$sourceSetName.kt") }
    )
}