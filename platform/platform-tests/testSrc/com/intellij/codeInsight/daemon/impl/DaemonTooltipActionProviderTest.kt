// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import java.io.File

/**
 * The hover tooltip must offer only a quick fix that it can invoke later.
 *
 * A file outside a content root is not indexable, so `DumbService.isUsableInCurrentContext` rejects a fix that is not
 * [DumbAware]. The invocation path applies that check in `ShowIntentionsPass.addAvailableFixesForGroups`. The tooltip
 * must apply it too, or it shows a fix that does nothing.
 */
class DaemonTooltipActionProviderTest : HeavyPlatformTestCase() {
  private val myEditors = mutableListOf<Editor>()

  override fun tearDown() {
    try {
      val editorFactory = EditorFactory.getInstance()
      myEditors.forEach(editorFactory::releaseEditor)
      myEditors.clear()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testFixOutsideContentRootIsOfferedOnlyWhenItIsDumbAware() {
    val file = createFileOutsideContentRoot()
    assertFalse("The premise of the test needs a file that is not indexable", isIndexable(file))

    assertNull(extractTooltipFix(file, PlainFix()))
    assertEquals(DumbAwareFix.TEXT, extractTooltipFix(file, DumbAwareFix())?.text)
  }

  fun testFixInsideContentRootIsAlwaysOffered() {
    val file = createFileInsideContentRoot()
    assertTrue("The premise of the test needs a file that is indexable", isIndexable(file))

    assertEquals(PlainFix.TEXT, extractTooltipFix(file, PlainFix())?.text)
    assertEquals(DumbAwareFix.TEXT, extractTooltipFix(file, DumbAwareFix())?.text)
  }

  /**
   * The project must be in smart mode. In dumb mode `CachedIntentions` drops a fix that is not [DumbAware] whatever the
   * file is, and then the test passes for the wrong reason.
   */
  private fun isIndexable(virtualFile: VirtualFile): Boolean {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    assertFalse("The project must be in smart mode", DumbService.getInstance(project).isDumb)
    return runReadActionBlocking { FileIndexFacade.getInstance(project).isIndexable(virtualFile) }
  }

  private fun extractTooltipFix(virtualFile: VirtualFile, fix: IntentionAction): IntentionAction? {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
    val editor = createEditorFor(psiFile)
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(0, FILE_TEXT.length)
      .descriptionAndTooltip("Test problem")
      .registerFix(fix, null, null, null, null)
      .createUnconditionally()
    return runReadActionBlocking { extractMostPriorityFixFromHighlightInfo(info, editor, psiFile) }
  }

  private fun createEditorFor(psiFile: PsiFile): Editor {
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
    val editor = EditorFactory.getInstance().createEditor(document, project, psiFile.virtualFile, false)
    myEditors.add(editor)
    return editor
  }

  private fun createFileInsideContentRoot(): VirtualFile {
    val directory = FileUtil.createTempDirectory("contentRoot", null)
    val virtualDirectory = refresh(directory)
    PsiTestUtil.addContentRoot(module, virtualDirectory)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return createFileIn(directory)
  }

  private fun createFileOutsideContentRoot(): VirtualFile = createFileIn(FileUtil.createTempDirectory("outside", null))

  private fun createFileIn(directory: File): VirtualFile {
    // Create the file outside the VFS, because the VFS marks a file that it creates as writable.
    val file = File(directory, "problem.txt")
    FileUtil.writeToFile(file, FILE_TEXT)
    return refresh(file)
  }

  private fun refresh(file: File): VirtualFile =
    WriteAction.computeAndWait<VirtualFile, RuntimeException> { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!! }

  private class PlainFix : AbstractIntentionAction() {
    override fun getText(): String = TEXT

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {}

    companion object {
      const val TEXT: String = "Plain test fix"
    }
  }

  private class DumbAwareFix : AbstractIntentionAction(), DumbAware {
    override fun getText(): String = TEXT

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {}

    companion object {
      const val TEXT: String = "Dumb aware test fix"
    }
  }

  companion object {
    private const val FILE_TEXT = "text"
  }
}
