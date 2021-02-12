// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ESCAPE
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.isDisposed
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.EditSourceOnDoubleClickHandler.isToggleEvent
import com.intellij.util.IJSwingUtilities
import com.intellij.util.Processor
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.streams.toList

abstract class EditorTabPreview(protected val diffProcessor: DiffRequestProcessor) : DiffPreview {
  protected val project get() = diffProcessor.project!!
  private val previewFile = EditorTabDiffPreviewVirtualFile(this)
  private val updatePreviewQueue =
    MergingUpdateQueue("updatePreviewQueue", 100, true, null, diffProcessor).apply {
      setRestartTimerOnAdd(true)
    }
  private val updatePreviewProcessor: DiffPreviewUpdateProcessor? get() = diffProcessor as? DiffPreviewUpdateProcessor

  var escapeHandler: Runnable? = null

  fun openWithDoubleClick(tree: ChangesTree) {
    installDoubleClickHandler(tree)
    installEnterKeyHandler(tree)
    installSelectionChangedHandler(tree) { updatePreview(false) }
  }

  fun openWithSingleClick(tree: ChangesTree) {
    //do not open file aggressively on start up, do it later
    DumbService.getInstance(project).smartInvokeLater {
      if (isDisposed(updatePreviewQueue)) return@smartInvokeLater

      installSelectionChangedHandler(tree) {
        if (!openPreview(false)) closePreview() // auto-close editor tab if nothing to preview
      }
    }
  }

  fun installNextDiffActionOn(component: JComponent) {
    DumbAwareAction.create { openPreview(true) }.apply {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
      registerCustomShortcutSet(component, diffProcessor)
    }
  }

  protected open fun isPreviewOnDoubleClickAllowed(): Boolean = true
  protected open fun isPreviewOnEnterAllowed(): Boolean = true

  private fun installDoubleClickHandler(tree: ChangesTree) {
    val oldDoubleClickHandler = tree.doubleClickHandler
    val newDoubleClickHandler = Processor<MouseEvent> { e ->
      if (isToggleEvent(tree, e)) return@Processor false

      isPreviewOnDoubleClickAllowed() && openPreview(true) || oldDoubleClickHandler?.process(e) == true
    }

    tree.doubleClickHandler = newDoubleClickHandler
    Disposer.register(diffProcessor, Disposable { tree.doubleClickHandler = oldDoubleClickHandler })
  }

  private fun installEnterKeyHandler(tree: ChangesTree) {
    val oldEnterKeyHandler = tree.enterKeyHandler
    val newEnterKeyHandler = Processor<KeyEvent> { e ->
      isPreviewOnEnterAllowed() && openPreview(false) || oldEnterKeyHandler?.process(e) == true
    }

    tree.enterKeyHandler = newEnterKeyHandler
    Disposer.register(diffProcessor, Disposable { tree.enterKeyHandler = oldEnterKeyHandler })
  }

  private fun installSelectionChangedHandler(tree: ChangesTree, handler: () -> Unit) =
    tree.addSelectionListener(
      Runnable {
        updatePreviewQueue.queue(DisposableUpdate.createDisposable(updatePreviewQueue, this) {
          if (!skipPreviewUpdate()) handler()
        })
      },
      updatePreviewQueue
    )

  protected abstract fun getCurrentName(): String?

  protected abstract fun hasContent(): Boolean

  protected open fun skipPreviewUpdate(): Boolean = ToolWindowManager.getInstance(project).isEditorComponentActive

  override fun updatePreview(fromModelRefresh: Boolean) {
    if (isPreviewOpen()) {
      updatePreviewProcessor?.refresh(false)
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(previewFile)
    }
    else {
      updatePreviewProcessor?.clear()
    }
  }

  override fun setPreviewVisible(isPreviewVisible: Boolean) {
    if (isPreviewVisible) openPreview(false) else closePreview()
  }

  private fun isPreviewOpen(): Boolean = FileEditorManager.getInstance(project).isFileOpen(previewFile)

  fun closePreview() {
    FileEditorManager.getInstance(project).closeFile(previewFile)
    updatePreviewProcessor?.clear()
  }

  fun openPreview(focusEditor: Boolean): Boolean {
    updatePreviewProcessor?.refresh(false)
    if (!hasContent()) return false

    val editors = openPreview(project, previewFile, focusEditor)

    escapeHandler?.let { handler ->
      for (editor in editors) {
        registerEscapeHandler(editor, handler)
      }
    }

    return true
  }

  private class EditorTabDiffPreviewVirtualFile(val preview: EditorTabPreview)
    : PreviewDiffVirtualFile(EditorTabDiffPreviewProvider(preview.diffProcessor) { preview.getCurrentName() }) {
    init {
      // EditorTabDiffPreviewProvider does not create new processor, so general assumptions of DiffVirtualFile are violated
      preview.diffProcessor.putContextUserData(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE, true)
      putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    }
  }

  companion object {
    fun openPreview(project: Project, file: PreviewDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor> {
      return VcsEditorTabFilesManager.getInstance().openFile(project, file, focusEditor)
    }

    fun registerEscapeHandler(editor: FileEditor, handler: Runnable) {
      EditorTabPreviewEscapeAction(handler).registerCustomShortcutSet(ESCAPE, editor.component, editor)
    }
  }
}

internal class EditorTabPreviewEscapeAction(private val escapeHandler: Runnable) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) = escapeHandler.run()
}

private class EditorTabDiffPreviewProvider(
  private val diffProcessor: DiffRequestProcessor,
  private val tabNameProvider: () -> String?
) : ChainBackedDiffPreviewProvider {
  override fun createDiffRequestProcessor(): DiffRequestProcessor {
    IJSwingUtilities.updateComponentTreeUI(diffProcessor.component)
    return diffProcessor
  }

  override fun getOwner(): Any = this

  override fun getEditorTabName(): @Nls String = tabNameProvider().orEmpty()

  override fun createDiffRequestChain(): DiffRequestChain? {
    if (diffProcessor is ChangeViewDiffRequestProcessor) {
      val selection = ListSelection.create(diffProcessor.allChanges.toList(), diffProcessor.currentChange)
      val producers = selection.map { it!!.createProducer(diffProcessor.project) }
      val chain = SimpleDiffRequestChain.fromProducers(producers.list)
      chain.index = producers.selectedIndex
      return chain
    }
    return null
  }
}
