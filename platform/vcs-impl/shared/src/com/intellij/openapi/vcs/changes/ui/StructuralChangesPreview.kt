// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.requestNavigate
import com.intellij.ui.ClickListener
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
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
    val clickListener = object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        if (clickCount != 1 || !SwingUtilities.isLeftMouseButton(event) || tree.selectionCount != 1) return false
        val path = TreeUtil.getPathForLocation(tree, event.x, event.y) ?: return false
        if (path == tree.selectionPath && path.lastPathComponent is ChangesBrowserStructuralNode<*> &&
            !TreeUtil.isLocationInExpandControl(tree, path, event.x, event.y)) {
          selectionChanged()
        }
        return false
      }
    }
    clickListener.installOn(tree)
    Disposer.register(disposable) {
      tree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelListener)
      tree.removeFocusListener(focusListener)
      clickListener.uninstall(tree)
    }
  }

  @RequiresEdt
  fun cancelPendingPreview() {
    pendingPreview?.cancel()
    pendingPreview = null
  }

  @RequiresEdt
  private fun selectionChanged() {
    cancelPendingPreview()
    if (!canPreviewSelection()) return
    val node = tree.selectionPath?.lastPathComponent as? ChangesBrowserStructuralNode<*> ?: return
    val model = tree.model
    val modality = ModalityState.defaultModalityState()
    pendingPreview = cs.launch(Dispatchers.EDT + modality.asContextElement()) {
      delay(Registry.intValue("ide.autoscroll.to.source.delay", 100).milliseconds)
      if (!isCurrentSelection(model, node)) return@launch
      val request = withContext(Dispatchers.Default) {
        readAction { node.getPreviewNavigationRequest() }
      } ?: return@launch
      if (!isCurrentSelection(model, node)) return@launch
      val options = NavigationOptions.defaultOptions()
        .requestFocus(false).preserveCaret(true).sourceNavigationOnly(true).recordAsBackHistory(false).forcePreviewTab(true)
      requestNavigate(tree.project, request, options, coroutineScope = this).await()
    }
  }

  @RequiresEdt
  private fun canPreviewSelection(): Boolean =
    !tree.project.isDisposed && !tree.isModelUpdateInProgress && tree.selectionCount == 1 && tree.isShowing

  @RequiresEdt
  private fun isCurrentSelection(model: TreeModel, node: ChangesBrowserStructuralNode<*>): Boolean =
    canPreviewSelection() && tree.hasFocus() && tree.model === model && tree.selectionPath?.lastPathComponent === node
}
