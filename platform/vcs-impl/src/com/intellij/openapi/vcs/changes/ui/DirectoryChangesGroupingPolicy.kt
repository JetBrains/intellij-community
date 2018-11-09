// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.DIRECTORY_CACHE
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_NODE_BUILDER
import javax.swing.tree.DefaultTreeModel

class DirectoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  private val innerPolicy = ChangesGroupingPolicyFactory.getInstance(project)?.createGroupingPolicy(model)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    DIRECTORY_POLICY.set(subtreeRoot, this)

    val grandParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot) ?: subtreeRoot
    HIERARCHY_UPPER_BOUND.set(subtreeRoot, grandParent)
    CACHING_ROOT.set(subtreeRoot, getCachingRoot(grandParent, subtreeRoot))

    return getParentNodeRecursive(nodePath, subtreeRoot)
  }

  private fun getParentNodeRecursive(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>) =
    getParentFromInnerPolicy(nodePath, subtreeRoot) ?: getParentNodeInternal(nodePath, subtreeRoot)

  @JvmName("getParentNodeInternal")
  internal fun getParentNodeInternal(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    generateSequence(nodePath.parent) { it.parent }.forEach { parentPath ->
      val cachingRoot = getCachingRoot(subtreeRoot)

      DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key]?.let {
        if (HIERARCHY_UPPER_BOUND.get(subtreeRoot) == it) {
          GRAND_PARENT_CANDIDATE.set(subtreeRoot, it)
          try {
            return getParentFromInnerPolicy(parentPath, subtreeRoot) ?: getPathNode(parentPath, subtreeRoot) ?: it
          }
          finally {
            GRAND_PARENT_CANDIDATE.set(subtreeRoot, null)
          }
        }
        return it
      }

      getPathNode(parentPath, subtreeRoot)?.let { return it }
    }

    return HIERARCHY_UPPER_BOUND.getRequired(subtreeRoot)
  }

  private fun getParentFromInnerPolicy(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    innerPolicy?.getParentNodeFor(nodePath, subtreeRoot)?.let {
      it.markAsHelperNode()
      return it
    }
    return null
  }

  private fun getPathNode(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    PATH_NODE_BUILDER.getRequired(subtreeRoot).apply(nodePath)?.let {
      it.markAsHelperNode()

      val grandParent = GRAND_PARENT_CANDIDATE.get(subtreeRoot) ?: getParentNodeRecursive(nodePath, subtreeRoot)
      val cachingRoot = getCachingRoot(subtreeRoot)

      model.insertNodeInto(it, grandParent, grandParent.childCount)
      DIRECTORY_CACHE.getValue(cachingRoot)[nodePath.key] = it
      return it
    }
    return null
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel): DirectoryChangesGroupingPolicy = DirectoryChangesGroupingPolicy(project, model)
  }

  companion object {
    @JvmField internal val DIRECTORY_POLICY = Key.create<DirectoryChangesGroupingPolicy>("ChangesTree.DirectoryPolicy")
    internal val GRAND_PARENT_CANDIDATE = Key.create<ChangesBrowserNode<*>?>("ChangesTree.GrandParentCandidate")
    internal val HIERARCHY_UPPER_BOUND = Key.create<ChangesBrowserNode<*>?>("ChangesTree.HierarchyUpperBound")
    internal val CACHING_ROOT = Key.create<ChangesBrowserNode<*>?>("ChangesTree.CachingRoot")

    internal fun getCachingRoot(subtreeRoot: ChangesBrowserNode<*>) = CACHING_ROOT.get(subtreeRoot) ?: subtreeRoot
  }
}