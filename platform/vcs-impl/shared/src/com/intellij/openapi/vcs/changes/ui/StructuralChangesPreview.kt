// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

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
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.requestNavigate
import com.intellij.ui.ClickListener
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

@ApiStatus.Internal
class StructuralChangesPreview(
  private val tree: ChangesListView,
  private val cs: CoroutineScope,
) {
  private var pendingPreview: Job? = null
  private var highlight: Disposable? = null

  @RequiresEdt
  fun install() {
    val disposable = cs.asDisposable()
    tree.addSelectionListener(Runnable { selectionChanged() }, disposable)
    val modelListener = PropertyChangeListener { cancelPendingPreview() }
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelListener)
    val focusListener = object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        cancelPendingPreview()
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
    pendingPreview?.cancel()
    pendingPreview = null
    clearHighlight()
  }

  @RequiresEdt
  private fun selectionChanged() {
    cancelPendingPreview()
    if (!canPreviewSelection()) return
    val node = tree.selectionPath?.lastPathComponent as? ChangesBrowserNode<*> ?: return
    val model = tree.model
    val modality = ModalityState.defaultModalityState()
    pendingPreview = cs.launch(Dispatchers.EDT + modality.asContextElement()) {
      delay(Registry.intValue("ide.autoscroll.to.source.delay", 100).milliseconds)
      if (!isCurrentSelection(model, node)) return@launch
      val preview = withContext(Dispatchers.Default) {
        readAction {
          val request = getPreviewNavigationRequest(node) ?: return@readAction null
          PreviewRequest(request, getHighlightTarget(node))
        }
      } ?: return@launch
      if (!isCurrentSelection(model, node)) return@launch
      val options = NavigationOptions.defaultOptions()
        .requestFocus(false).preserveCaret(true).sourceNavigationOnly(true).recordAsBackHistory(false).forcePreviewTab(true)
      requestNavigate(tree.project, preview.request, options, coroutineScope = this).await()
      if (isCurrentSelection(model, node)) preview.highlightTarget?.let { showHighlight(it) }
    }
  }

  @RequiresEdt
  private fun canPreviewSelection(): Boolean =
    !tree.project.isDisposed && StructuralChangesViewSettings.isEnabled(tree.project) &&
    !tree.isModelUpdateInProgress && tree.selectionCount == 1 && tree.isShowing

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
    highlight = tree.project.service<StructuralChangesPreviewHighlighter>().highlight(target.file, target.range, target.modificationStamp)
  }

  @RequiresEdt
  private fun clearHighlight() {
    val previous = highlight
    highlight = null
    previous?.let { Disposer.dispose(it) }
  }

  private data class PreviewRequest(val request: NavigationRequest, val highlightTarget: HighlightTarget?)

  private data class HighlightTarget(val file: VirtualFile, val range: TextRange, val modificationStamp: Long)
}
