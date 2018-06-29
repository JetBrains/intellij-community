// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.Companion.DIRECTORY_POLICY
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.Companion.GRAND_PARENT_CANDIDATE
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.Companion.HIERARCHY_UPPER_BOUND
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.Companion.getCachingRoot
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.DIRECTORY_CACHE
import javax.swing.tree.DefaultTreeModel

private const val PROJECT_ROOT_TAG = "<Project Root>"

open class ChangesModuleGroupingPolicy(val myProject: Project, val myModel: DefaultTreeModel) : ChangesGroupingPolicy {
  private val myIndex = ProjectFileIndex.getInstance(myProject)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    if (myProject.isDefault) return null

    val vFile = nodePath.resolve()
    if (vFile != null && Comparing.equal(vFile, myIndex.getContentRootForFile(vFile, HIDE_EXCLUDED_FILES))) {
      val module = myIndex.getModuleForFile(vFile, HIDE_EXCLUDED_FILES)
      return getNodeForModule(module, nodePath, subtreeRoot)
    }
    return null
  }

  private fun getNodeForModule(module: Module?, nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    val cachingRoot = getCachingRoot(subtreeRoot)

    MODULE_CACHE.getValue(cachingRoot)[module]?.let { return it }

    val policy = DIRECTORY_POLICY.get(subtreeRoot)
    val parent = GRAND_PARENT_CANDIDATE.get(subtreeRoot) ?: if (policy != null && !isTopLevel(nodePath)) policy.getParentNodeInternal(
      nodePath, subtreeRoot)
    else HIERARCHY_UPPER_BOUND.getRequired(subtreeRoot)
    val node = if (module == null) ChangesBrowserNode.create(myProject, PROJECT_ROOT_TAG) else ChangesBrowserModuleNode(module)

    myModel.insertNodeInto(node, parent, parent.childCount)
    MODULE_CACHE.getValue(cachingRoot)[module] = node
    DIRECTORY_CACHE.getValue(cachingRoot)[nodePath.key] = node

    return node
  }

  private fun isTopLevel(nodePath: StaticFilePath): Boolean {
    val parentFile = nodePath.parent?.resolve()
    return parentFile == null || myIndex.getContentRootForFile(parentFile, HIDE_EXCLUDED_FILES) == null
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel): ChangesGroupingPolicy = ChangesModuleGroupingPolicy(project, model)
  }

  companion object {
    val MODULE_CACHE: NotNullLazyKey<MutableMap<Module?, ChangesBrowserNode<*>>, ChangesBrowserNode<*>> = NotNullLazyKey.create<MutableMap<Module?, ChangesBrowserNode<*>>, ChangesBrowserNode<*>>(
      "ChangesTree.ModuleCache") { _ -> mutableMapOf() }
    val HIDE_EXCLUDED_FILES: Boolean = Registry.`is`("ide.hide.excluded.files")
  }
}