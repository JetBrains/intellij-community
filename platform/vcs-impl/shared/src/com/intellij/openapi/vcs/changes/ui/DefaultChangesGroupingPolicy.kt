// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider
import com.intellij.platform.vcs.changes.ChangesUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
object NoneChangesGroupingPolicy : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath,
                                node: ChangesBrowserNode<*>,
                                subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? = null
}

object NoneChangesGroupingFactory : ChangesGroupingPolicyFactory() {
  override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy =
    NoneChangesGroupingPolicy
}

@ApiStatus.Internal
class DefaultChangesGroupingPolicy(val project: Project, model: DefaultTreeModel) : SimpleChangesGroupingPolicy<Any>(model) {
  override fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Any? {
    if (isMergeConflict(nodePath, node)) {
      return CONFLICTS_NODE_FLAG
    }
    return null
  }

  override fun createGroupRootNode(value: Any): ChangesBrowserNode<*> {
    assert(value == CONFLICTS_NODE_FLAG)
    val conflictsRoot = ChangesBrowserConflictsNode(project)
    conflictsRoot.markAsHelperNode()
    return conflictsRoot
  }

  private fun isMergeConflict(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Boolean {
    if (node is ChangesGroupingPolicy.CompatibilityPlaceholderChangesBrowserNode) {
      val vFile = nodePath.resolve() ?: return false
      val status = ChangesTreeCompatibilityProvider.getInstance().getFileStatus(project, vFile)
      return ChangesUtil.isMergeConflict(status)
    }

    if (node is ChangesBrowserChangeNode) {
      return ChangesUtil.isMergeConflict(node.userObject)
    }
    return false
  }

  companion object {
    private val CONFLICTS_NODE_FLAG = Any()
  }

  internal class Factory @JvmOverloads constructor(private val forLocalChanges: Boolean = false) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy {
      return when {
        forLocalChanges -> DefaultChangesGroupingPolicy(project, model)
        else -> NoneChangesGroupingPolicy
      }
    }
  }
}
