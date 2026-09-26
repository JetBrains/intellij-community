// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.platform.projectView.impl.ProjectViewUpdater
import com.intellij.platform.projectView.impl.ProjectViewUpdaterProgressReporter
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeModelAdapter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.event.TreeModelEvent
import kotlin.time.Duration.Companion.milliseconds

/**
 * Translates [ScopeViewTreeModel] events into Project View node updates.
 *
 * Unlike [com.intellij.platform.projectView.impl.TreeStructureProjectViewUpdater], this one subscribes to nothing: [ScopeViewTreeModel] already tracks
 * VFS and PSI changes (through [com.intellij.ui.tree.project.ProjectFileNodeUpdater]) as well as bookmarks,
 * problems, file statuses, cut/copy and editor open/close, and reports all of it as tree model events.
 */
internal class ScopeViewProjectViewUpdater(private val treeModel: ScopeViewTreeModel) : ProjectViewUpdater {
  override suspend fun continuouslyUpdatePane(pane: ProjectViewPaneModel, progressReporter: ProjectViewUpdaterProgressReporter) {
    val model = pane as ScopePaneModel
    val events = Channel<ScopeViewUpdateEvent>(capacity = Channel.UNLIMITED)
    val listener = object : TreeModelAdapter() {
      override fun process(event: TreeModelEvent, type: EventType) {
        // Report before sending, so the submitted count is never lower than what's actually in the queue.
        progressReporter.eventSubmitted()
        val changedNode = event.treePath?.lastPathComponent as? AbstractTreeNode<*>
        events.trySend(ScopeViewUpdateEvent(changedNode, isStructural = type != EventType.NodesChanged))
      }
    }
    treeModel.addTreeModelListener(listener)
    try {
      processEvents(model, events, progressReporter)
    }
    finally {
      treeModel.removeTreeModelListener(listener)
    }
  }

  private suspend fun processEvents(
      model: ScopePaneModel,
      events: Channel<ScopeViewUpdateEvent>,
      progressReporter: ProjectViewUpdaterProgressReporter,
  ) {
    for (first in events) {
      // Coalesce a burst: wait a little, then drain everything that has accumulated.
      delay(10.milliseconds)
      val batch = ArrayList<ScopeViewUpdateEvent>()
      batch.add(first)
      while (true) {
        val next = events.tryReceive().getOrNull() ?: break
        batch.add(next)
      }
      process(model, batch)
      // Report after processing, so the resulting updateNode calls have already bumped the node epoch.
      progressReporter.eventsProcessed(batch.size)
    }
  }

  private suspend fun process(model: ScopePaneModel, batch: List<ScopeViewUpdateEvent>) {
    // A whole-tree structure change (ScopeViewTreeModel.invalidate) supersedes everything else in the batch.
    if (batch.any { it.isStructural && it.changedNode == null }) {
      updateAll(model)
      return
    }
    // The model reuses its node instances across rebuilds, so identity is the way to find them here.
    val structural = identitySetOf(batch.filter { it.isStructural }.mapNotNull { it.changedNode })
    val presentation = identitySetOf(batch.filterNot { it.isStructural }.mapNotNull { it.changedNode })
    if (structural.isEmpty() && presentation.isEmpty()) return
    model.visitTree(allowLoading = false) { node ->
      val legacyNode = node.userObject.elementDescriptor
      when (legacyNode) {
        in structural -> model.updateNode(node.id) { it.deep = true }
        in presentation -> model.updateNode(node.id) { it.deep = false }
      }
      TreeVisitor.Action.CONTINUE
    }
  }

  private suspend fun updateAll(model: ScopePaneModel) {
    // visitTree starts at the (single) root, so a deep update of it reloads the whole loaded tree.
    model.visitTree(allowLoading = false) { node ->
      model.updateNode(node.id) { it.deep = true }
      TreeVisitor.Action.SKIP_CHILDREN
    }
  }
}

private fun identitySetOf(nodes: List<AbstractTreeNode<*>>): Set<Any> =
  Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).also { it.addAll(nodes) }

private data class ScopeViewUpdateEvent(val changedNode: AbstractTreeNode<*>?, val isStructural: Boolean)
