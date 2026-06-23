// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.folding

import com.intellij.compose.ide.plugin.resources.ComposeResourcesAllSourceSets
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.utils.editor.reloadFromDisk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull as kAssertNotNull

private const val DEFAULT_STRINGS_FILE_PATH = "composeApp/src/commonMain/composeResources/values/strings.xml"
private const val ORIGINAL_STRING_VALUE = "<string name=\"test\">test</string>"
private const val PRIORITIZED_STRING_TEXT = "default value"
private const val PRIORITIZED_STRING_VALUE = "<string name=\"test\">$PRIORITIZED_STRING_TEXT</string>"

@ComposeResourcesAllSourceSets
class ComposeResourcesFoldingTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test string resources are folded`() = doFoldingTest { descriptors ->
    val folding = kAssertNotNull(
      descriptors.findByResourceRef("Res.string.test"),
      "Folding descriptor for Res.string.test should exist",
    )
    assertEquals("\"test\"", folding.placeholderText)
  }

  @Test
  fun `test default value is preferred over translations`() =
    doFoldingTest(prepareResources = { replaceDefaultStringValue() }) { descriptors ->
      val folding = kAssertNotNull(
        descriptors.findByResourceRef("Res.string.test"),
        "Folding descriptor for Res.string.test should exist",
      )
      assertEquals("\"$PRIORITIZED_STRING_TEXT\"", folding.placeholderText)
    }

  @Test
  fun `test plural resources are folded`() = doFoldingTest { descriptors ->
    val folding = kAssertNotNull(
      descriptors.findByResourceRef("Res.plurals.test"),
      "Folding descriptor for Res.plurals.test should exist",
    )
    assertEquals("\"%d zero\"", folding.placeholderText)
  }

  @Test
  fun `test non-string resources are not folded`() = doFoldingTest { descriptors ->
    assertNull(descriptors.findByResourceRef("Res.drawable.test"), "Folding descriptor for Res.drawable.test should not exist")
    assertNull(descriptors.findByResourceRef("Res.font.test"), "Folding descriptor for Res.font.test should not exist")
  }

  @Test
  fun `test folding is collapsed by default`() = doFoldingTest { descriptors ->
    assertTrue(descriptors.isNotEmpty(), "There should be at least one folding descriptor")
    descriptors.filterNotNull().forEach { descriptor ->
      assertEquals(true, descriptor.isCollapsedByDefault, "Folding should be collapsed by default")
    }
  }

  @Test
  fun `test quick mode returns no folding`() = doFoldingTest(quick = true) { descriptors ->
    assertTrue(descriptors.isEmpty(), "Quick mode should return no folding descriptors")
  }

  private fun doFoldingTest(
    quick: Boolean = false,
    prepareResources: suspend () -> Unit = {},
    assertions: (Array<out FoldingDescriptor?>) -> Unit,
  ) = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      try {
        prepareResources()
        codeInsightFixture.configureFromExistingVirtualFile(
          canonicalProjectFile("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
        )

        val foldingBuilder = ComposeResourcesFoldingBuilder()
        val psiFile = codeInsightFixture.file
        val document = kAssertNotNull(
          PsiDocumentManager.getInstance(project).getDocument(psiFile),
          "Document should not be null",
        )
        val descriptors = foldingBuilder.buildFoldRegions(psiFile, document, quick)

        assertions(descriptors)
      }
      finally {
        revertUnsavedDocuments()
      }
    }
  }

  /**
   * The Gradle fixture restores the snapshotted files on the disk, but it cannot restore an unsaved document of
   * [canonicalProjectFile]: that document belongs to a second virtual file for the same path, and the rollback then
   * fails with a memory-disk conflict.
   */
  private fun revertUnsavedDocuments() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val unsavedDocuments = fileDocumentManager.unsavedDocuments
    if (unsavedDocuments.isEmpty()) return
    runWriteAction {
      unsavedDocuments.forEach { it.reloadFromDisk() }
    }
  }

  /**
   * The resource resolution goes through [canonicalProjectFile], so the document of that virtual file must be modified.
   * [revertUnsavedDocuments] drops the modification when the test ends.
   */
  private fun replaceDefaultStringValue() {
    val stringsFile = canonicalProjectFile(DEFAULT_STRINGS_FILE_PATH)
    val documentManager = PsiDocumentManager.getInstance(project)
    val psiFile = kAssertNotNull(
      PsiManager.getInstance(project).findFile(stringsFile),
      "Default strings.xml should have a PSI file",
    )
    val document = kAssertNotNull(
      documentManager.getDocument(psiFile),
      "Default strings.xml should have a document",
    )

    val valueOffset = document.text.indexOf(ORIGINAL_STRING_VALUE)
    assertTrue(valueOffset >= 0, "Default string resource should exist")

    WriteCommandAction.runWriteCommandAction(project) {
      document.replaceString(
        valueOffset,
        valueOffset + ORIGINAL_STRING_VALUE.length,
        PRIORITIZED_STRING_VALUE,
      )
      documentManager.commitDocument(document)
    }
  }

  private fun Array<out FoldingDescriptor?>.findByResourceRef(ref: String): FoldingDescriptor? =
    find { it?.element?.psi?.text?.contains(ref) == true }
}
