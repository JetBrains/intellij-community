// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffManager
import com.intellij.diff.DiffManagerImpl
import com.intellij.diff.merge.IterativeResolveSupport
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Container
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane
import kotlin.io.path.writeText

/**
 * The iterative flow keeps a partial resolution in the document. It writes the working file only when it marks the file processed.
 * A provider that reads "yours" from the working file, such as Perforce, depends on that.
 */
@TestApplication
@RegistryKey(key = "vcs.merge.conflict.iterative.resolution", value = "true")
@RegistryKey(key = "vcs.merge.conflict.file.suggestion", value = "true")
internal class MultipleFileMergeDialogTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project get() = projectFixture.get()
  private val tempDirFixture = tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun replaceDialogPeerFactory() {
    ApplicationManager.getApplication().replaceService(DialogWrapperPeerFactory::class.java, RootPaneDialogPeerFactory(), disposable)
  }

  @Test
  fun cancelInViewerKeepsYoursOnDisk(): Unit = timeoutRunBlocking {
    val conflict = createConflict()
    replaceMergeViewer(disposable) { request ->
      val model = IterativeResolveSupport.getData(request)!!
      runWriteAction { model.resolveAllChangesAutomatically() }
      request.applyResult(MergeResult.CANCEL)
    }
    val questions = answerQuestions(Messages.YES)

    withMergeDialog(conflict) { dialog ->
      withContext(Dispatchers.EDT) { dialog.clickButton(VcsBundle.message("multiple.file.iterative.merge.resolve.manually")) }
      assertEquals(PARTIALLY_MERGED, documentText(conflict.file))
      assertEquals(YOURS, fileText(conflict.file))

      withContext(Dispatchers.EDT) { dialog.doCancelAction() }
    }

    assertEquals(listOf(DISCARD_QUESTION), questions)
    assertEquals(YOURS, fileText(conflict.file))
    assertEquals(YOURS, documentText(conflict.file))
    assertEquals(emptyList<VirtualFile>(), conflict.provider.resolvedFiles)
  }

  @Test
  fun resolveAutomaticallyKeepsYoursOnDisk(): Unit = timeoutRunBlocking {
    val conflict = createConflict()
    val questions = answerQuestions(Messages.YES)

    withMergeDialog(conflict) { dialog ->
      withContext(Dispatchers.EDT) { dialog.clickButton(VcsBundle.message("multiple.file.iterative.merge.resolve.automatically")) }
      assertEquals(PARTIALLY_MERGED, documentText(conflict.file))
      assertEquals(YOURS, fileText(conflict.file))

      withContext(Dispatchers.EDT) { dialog.doCancelAction() }
    }

    assertEquals(listOf(DISCARD_QUESTION), questions)
    assertEquals(YOURS, fileText(conflict.file))
    assertEquals(YOURS, documentText(conflict.file))
    assertEquals(emptyList<VirtualFile>(), conflict.provider.resolvedFiles)
  }

  /** Esc and the window close button call `doCancelAction`, as the Close button does. */
  @Test
  fun cancelKeepsDialogOpenWhenUserContinuesMerge(): Unit = timeoutRunBlocking {
    val conflict = createConflict()
    val questions = answerQuestions(Messages.NO)

    withMergeDialog(conflict) { dialog ->
      withContext(Dispatchers.EDT) { dialog.clickButton(VcsBundle.message("multiple.file.iterative.merge.resolve.automatically")) }
      withContext(Dispatchers.EDT) { dialog.doCancelAction() }

      assertEquals(listOf(DISCARD_QUESTION), questions)
      assertFalse(dialog.isDisposed)
      assertEquals(PARTIALLY_MERGED, documentText(conflict.file))
    }
  }

  @Test
  fun closeSavesAndMarksResolvedFile(): Unit = timeoutRunBlocking {
    val conflict = createConflict()
    replaceMergeViewer(disposable) { request ->
      IterativeResolveSupport.getData(request)!!.acceptRevisionForSide(Side.RIGHT)
      request.applyResult(MergeResult.RESOLVED)
    }

    withMergeDialog(conflict) { dialog ->
      withContext(Dispatchers.EDT) { dialog.clickButton(VcsBundle.message("multiple.file.iterative.merge.resolve.manually")) }
      assertEquals(THEIRS, documentText(conflict.file))
      assertEquals(YOURS, fileText(conflict.file))

      withContext(Dispatchers.EDT) { dialog.doCancelAction() }
      assertEquals(listOf(conflict.file), dialog.processedFiles)
    }

    assertEquals(THEIRS, fileText(conflict.file))
    assertEquals(listOf(conflict.file), conflict.provider.resolvedFiles)
  }

  @Test
  fun closeKeepsConflictWhenSaveFails(): Unit = timeoutRunBlocking {
    val conflict = createConflict()
    replaceMergeViewer(disposable) { request ->
      IterativeResolveSupport.getData(request)!!.acceptRevisionForSide(Side.RIGHT)
      request.applyResult(MergeResult.RESOLVED)
    }
    ExtensionTestUtil.addExtensions(FileDocumentSynchronizationVetoer.EP_NAME, listOf(SaveVetoer(conflict.file)), disposable)

    withMergeDialog(conflict) { dialog ->
      withContext(Dispatchers.EDT) { dialog.clickButton(VcsBundle.message("multiple.file.iterative.merge.resolve.manually")) }
      withContext(Dispatchers.EDT) { dialog.doCancelAction() }
      assertEquals(emptyList<VirtualFile>(), dialog.processedFiles)
    }

    assertEquals(YOURS, fileText(conflict.file))
    assertEquals(YOURS, documentText(conflict.file))
    assertEquals(emptyList<VirtualFile>(), conflict.provider.resolvedFiles)
  }

  /** Answers each message dialog with [answer] and returns the messages that the dialogs showed. */
  private fun answerQuestions(answer: Int): List<String> {
    val questions = CopyOnWriteArrayList<String>()
    TestDialogManager.setTestDialog(TestDialog { message -> questions.add(message); answer }, disposable)
    return questions
  }

  private suspend fun createConflict(): Conflict {
    val path = tempDirFixture.get().resolve("conflict.txt")
    path.writeText(YOURS)
    val file = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!! }
    return Conflict(file, WorkingFileMergeProvider())
  }

  private suspend fun withMergeDialog(conflict: Conflict, action: suspend (MultipleFileMergeDialog) -> Unit) {
    val dialog = withContext(Dispatchers.EDT) {
      MultipleFileMergeDialog(project, listOf(conflict.file), conflict.provider, MergeDialogCustomizer())
    }
    try {
      action(dialog)
    }
    finally {
      withContext(Dispatchers.EDT + NonCancellable) {
        if (!dialog.isDisposed) dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
      }
    }
  }
}

private const val BASE = "alpha\n=\nbeta\n=\ngamma\n"
private const val YOURS = "ALPHA\n=\nmine\n=\ngamma\n"
private const val THEIRS = "alpha\n=\ntheirs\n=\nGAMMA\n"

/** [BASE] with the two non-conflicting changes applied and the conflict left unresolved. */
private const val PARTIALLY_MERGED = "ALPHA\n=\nbeta\n=\nGAMMA\n"

private val DISCARD_QUESTION: String get() = VcsBundle.message("multiple.file.iterative.merge.close.confirmation.message")

private class Conflict(val file: VirtualFile, val provider: WorkingFileMergeProvider)

/** Reads "yours" from the working file, as `PerforceMergeProvider` does. */
private class WorkingFileMergeProvider : MergeProvider {
  val resolvedFiles: MutableList<VirtualFile> = CopyOnWriteArrayList()

  override fun loadRevisions(file: VirtualFile): MergeData = MergeData().apply {
    CURRENT = file.contentsToByteArray()
    ORIGINAL = BASE.toByteArray()
    LAST = THEIRS.toByteArray()
  }

  override fun conflictResolvedForFile(file: VirtualFile) {
    resolvedFiles.add(file)
  }

  override fun isBinary(file: VirtualFile): Boolean = false
}

/** Vetoes each save of the document of [file], so that the document stays unsaved as after an I/O error. */
private class SaveVetoer(private val file: VirtualFile) : FileDocumentSynchronizationVetoer() {
  override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean =
    FileDocumentManager.getInstance().getFile(document) != file
}

/** Replaces the three-side merge viewer with [onShow], which gets the merge request instead of a window. */
private fun replaceMergeViewer(disposable: Disposable, onShow: (MergeRequest) -> Unit) {
  val diffManager = object : DiffManagerImpl() {
    override fun showMergeBuiltin(project: Project?, request: MergeRequest) = onShow(request)
  }
  ApplicationManager.getApplication().replaceService(DiffManager::class.java, diffManager, disposable)
}

/**
 * Returns the content of the [file] in the VFS, which is what `PerforceMergeProvider` reads as "yours".
 * The VFS can write a saved document to disk asynchronously, so a read of the disk can return older content.
 */
private suspend fun fileText(file: VirtualFile): String = readAction { VfsUtilCore.loadText(file) }

private suspend fun documentText(file: VirtualFile): String = readAction {
  FileDocumentManager.getInstance().getDocument(file)!!.text
}

private fun MultipleFileMergeDialog.clickButton(text: String) {
  UIUtil.findComponentsOfType(rootPane, JButton::class.java).single { it.text == text }.doClick()
}

/** Creates [RootPaneDialogPeer] for a dialog that has a project. `MultipleFileMergeDialog` uses this overload. */
private class RootPaneDialogPeerFactory : DialogWrapperPeerFactoryImpl() {
  override fun createPeer(
    wrapper: DialogWrapper,
    project: Project?,
    canBeParent: Boolean,
    ideModalityType: DialogWrapper.IdeModalityType,
  ): DialogWrapperPeer = RootPaneDialogPeer(wrapper, project, canBeParent, ideModalityType)
}

/**
 * Gives the dialog a root pane that holds its content. In unit test mode, [DialogWrapperPeerImpl] wraps a headless dialog,
 * and that dialog has no root pane and no content pane.
 */
private class RootPaneDialogPeer(
  wrapper: DialogWrapper,
  project: Project?,
  canBeParent: Boolean,
  ideModalityType: DialogWrapper.IdeModalityType,
) : DialogWrapperPeerImpl(wrapper, project, canBeParent, ideModalityType) {
  private val pane = JRootPane()

  override fun getRootPane(): JRootPane = pane

  override fun getContentPane(): Container = pane.contentPane

  override fun setContentPane(content: JComponent) {
    pane.contentPane = content
  }
}
