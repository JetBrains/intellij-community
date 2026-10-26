// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.requestNavigate
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.intellij.ui.ClickListener
import com.intellij.ui.ComponentUtil
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreeModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the selected file or hunk while the Changes tree keeps the focus. The diff preview of the Commit tool window shows the file, scrolls
 * to the selected hunk, and dims the other lines. Without a diff preview, a source preview tab shows the hunk. A diff next to the tree in
 * the tool window replaces this preview.
 */
@ApiStatus.Internal
class StructuralChangesPreview(
  private val tree: ChangesListView,
  private val cs: CoroutineScope,
) {
  private var pendingPreview: Job? = null
  private var highlight: Disposable? = null
  private var highlightOwner: Any? = null

  @RequiresEdt
  fun install() {
    val disposable = cs.asDisposable()
    tree.addSelectionListener(Runnable { selectionChanged() }, disposable)
    val modelListener = PropertyChangeListener {
      // Only a shown or a pending preview follows the refresh, so a closed diff tab stays closed.
      val restart = pendingPreview?.isActive == true || highlight != null
      cancelPendingJob()
      if (restart) SwingUtilities.invokeLater { treeModelUpdated() }
    }
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelListener)
    val focusListener = object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        cancelPendingJob()
      }
    }
    tree.addFocusListener(focusListener)
    val keyListener = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ESCAPE) cancelPendingPreview()
      }
    }
    tree.addKeyListener(keyListener)
    val hierarchyListener = HierarchyListener { event ->
      if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !tree.isShowing) cancelPendingPreview()
    }
    tree.addHierarchyListener(hierarchyListener)
    val connection = tree.project.messageBus.connect(disposable)
    connection.subscribe(ChangesViewModifier.TOPIC, ChangesViewModifier.ChangesViewModifierListener {
      UIUtil.invokeLaterIfNeeded {
        if (!StructuralChangesViewSettings.isEnabled(tree.project)) cancelPendingPreview()
      }
    })
    val clickListener = object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        if (clickCount != 1 || !SwingUtilities.isLeftMouseButton(event) || tree.selectionCount != 1) return false
        val path = TreeUtil.getPathForLocation(tree, event.x, event.y) ?: return false
        if (path == tree.selectionPath && !TreeUtil.isLocationInExpandControl(tree, path, event.x, event.y)) {
          selectionChanged()
        }
        return false
      }
    }
    clickListener.installOn(tree)
    Disposer.register(disposable) {
      UIUtil.invokeLaterIfNeeded {
        cancelPendingPreview()
        tree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelListener)
        tree.removeFocusListener(focusListener)
        tree.removeKeyListener(keyListener)
        tree.removeHierarchyListener(hierarchyListener)
        clickListener.uninstall(tree)
      }
    }
  }

  @RequiresEdt
  fun cancelPendingPreview() {
    cancelPendingJob()
    clearHighlight()
  }

  @RequiresEdt
  private fun cancelPendingJob() {
    pendingPreview?.cancel()
    pendingPreview = null
  }

  /** A refresh rebuilds the tree. The dim stays while the tree selects the same hunk after the refresh. */
  @RequiresEdt
  private fun treeModelUpdated() {
    if (tree.isModelUpdateInProgress) return
    val node = tree.selectionPath?.lastPathComponent as? ChangesBrowserNode<*>
    if (highlight != null && node != null && node.userObject == highlightOwner) return
    selectionChanged()
  }

  @RequiresEdt
  private fun selectionChanged() {
    // The model listener checks the selection after the update ends.
    if (tree.isModelUpdateInProgress) return
    cancelPendingPreview()
    if (!canPreviewSelection()) return
    val node = tree.selectionPath?.lastPathComponent as? ChangesBrowserNode<*> ?: return
    val model = tree.model
    val modality = ModalityState.defaultModalityState()
    pendingPreview = cs.launch(Dispatchers.EDT + modality.asContextElement()) {
      delay(Registry.intValue("ide.autoscroll.to.source.delay", 100).milliseconds)
      if (!isCurrentSelection(model, node)) return@launch
      val diffPreview = DataManager.getInstance().getDataContext(tree).getData(DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW) as? EditorTabDiffPreview
      if (diffPreview != null && isDiffable(node)) showDiff(diffPreview, node, model)
      else showSource(node, model)
    }
  }

  private fun isDiffable(node: ChangesBrowserNode<*>): Boolean =
    node is ChangesBrowserChangeNode || node is ChangesBrowserStructuralNode<*> && node.getChangeNode() != null

  private suspend fun showDiff(preview: EditorTabDiffPreview, node: ChangesBrowserNode<*>, model: TreeModel) {
    if (!preview.isPreviewOpen()) preview.openPreview(false)
    val lines = (node as? ChangesBrowserStructuralNode<*>)?.getDiffLines() ?: return
    val viewer = awaitViewer(preview, node.diffFile(), model, node) ?: return
    val handle = tree.project.service<StructuralChangesPreviewHighlighter>().highlightDiff(viewer, lines) ?: return
    keepHighlight(handle, node.userObject)
  }

  /** Waits until the preview shows the file of the node and finishes its diff. The preview follows the tree selection on its own. */
  private suspend fun awaitViewer(preview: EditorTabDiffPreview, file: VirtualFile?, model: TreeModel, node: ChangesBrowserNode<*>): DiffViewerBase? {
    repeat(VIEWER_WAIT_ATTEMPTS) {
      if (!isCurrentSelection(model, node)) return null
      val viewer = tree.project.service<StructuralChangesPreviewHighlighter>().findDiffViewer(preview.previewFile)
      if (viewer != null && !viewer.hasPendingRediff() && (file == null || viewer.request.contents.any { (it as? DocumentContent)?.highlightFile == file })) {
        return viewer
      }
      delay(VIEWER_WAIT_STEP)
    }
    return null
  }

  private suspend fun showSource(node: ChangesBrowserNode<*>, model: TreeModel) {
    val preview = withContext(Dispatchers.Default) {
      readAction {
        val request = getPreviewNavigationRequest(node) ?: return@readAction null
        PreviewRequest(request, getHighlightTarget(node))
      }
    } ?: return
    if (!isCurrentSelection(model, node)) return
    val options = NavigationOptions.defaultOptions()
      .requestFocus(false).preserveCaret(true).sourceNavigationOnly(true).recordAsBackHistory(false).forcePreviewTab(true)
    coroutineScope { requestNavigate(tree.project, preview.request, options, coroutineScope = this).await() }
    if (isCurrentSelection(model, node)) preview.highlightTarget?.let { showHighlight(it) }
  }

  @RequiresEdt
  private fun canPreviewSelection(): Boolean =
    !tree.project.isDisposed && StructuralChangesViewSettings.isEnabled(tree.project) &&
    !tree.isModelUpdateInProgress && tree.selectionCount == 1 && tree.isShowing && !hasSplitterPreview()

  /** True when the tool window shows a diff next to the tree. That diff follows the selection, so this preview opens no tab. */
  @RequiresEdt
  private fun hasSplitterPreview(): Boolean =
    ComponentUtil.getParentOfType(PreviewDiffSplitterComponent::class.java, tree)?.secondComponent != null

  @RequiresEdt
  private fun isCurrentSelection(model: TreeModel, node: ChangesBrowserNode<*>): Boolean =
    canPreviewSelection() && tree.hasFocus() && tree.model === model && tree.selectionPath?.lastPathComponent === node

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun getPreviewNavigationRequest(node: ChangesBrowserNode<*>): NavigationRequest? {
    if (node is ChangesBrowserStructuralNode<*>) return node.getPreviewNavigationRequest()
    val file = when (node) {
      is ChangesBrowserChangeNode -> node.userObject.afterRevision?.file?.virtualFile
      is ChangesBrowserFileNode -> node.userObject
      is ChangesBrowserFilePathNode -> node.userObject.virtualFile
      else -> null
    } ?: return null
    if (!file.isValid || file.isDirectory) return null
    return NavigationRequest.sourceNavigationRequest(tree.project, file, -1)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun getHighlightTarget(node: ChangesBrowserNode<*>): HighlightTarget? {
    if (node !is ChangesBrowserStructuralNode<*>) return null
    val range = node.getPreviewHighlightRange() ?: return null
    val file = node.getPreviewFile() ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    if (range.isEmpty || range.endOffset > document.textLength) return null
    return HighlightTarget(file, range, document.modificationStamp)
  }

  @RequiresEdt
  private fun showHighlight(target: HighlightTarget) {
    val handle = tree.project.service<StructuralChangesPreviewHighlighter>().highlight(target.file, target.range, target.modificationStamp)
    handle?.let { keepHighlight(it, owner = null) }
  }

  /** Keeps the highlight until it ends. A closed diff tab or input in the editor ends it, and then the preview counts as closed. */
  @RequiresEdt
  private fun keepHighlight(handle: Disposable, owner: Any?) {
    highlight = handle
    highlightOwner = owner
    Disposer.register(handle) {
      if (highlight === handle) {
        highlight = null
        highlightOwner = null
      }
    }
  }

  @RequiresEdt
  private fun clearHighlight() {
    val previous = highlight
    highlight = null
    highlightOwner = null
    previous?.let { Disposer.dispose(it) }
  }

  private fun ChangesBrowserNode<*>.diffFile(): VirtualFile? = when (this) {
    is ChangesBrowserStructuralNode<*> -> getPreviewFile()
    is ChangesBrowserChangeNode -> userObject.afterRevision?.file?.virtualFile
    else -> null
  }

  private data class PreviewRequest(val request: NavigationRequest, val highlightTarget: HighlightTarget?)

  private data class HighlightTarget(val file: VirtualFile, val range: TextRange, val modificationStamp: Long)

  private companion object {
    const val VIEWER_WAIT_ATTEMPTS = 100
    val VIEWER_WAIT_STEP = 50.milliseconds
  }
}
