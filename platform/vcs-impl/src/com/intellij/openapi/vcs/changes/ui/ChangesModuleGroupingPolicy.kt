// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.Companion.DIRECTORY_POLICY
import java.util.*
import javax.swing.tree.DefaultTreeModel

class ChangesModuleGroupingPolicy(val myProject: Project, val myModel: DefaultTreeModel) : ChangesGroupingPolicy {
  private val myModuleCache = HashMap<Module?, ChangesBrowserNode<*>>()
  private val myIndex = ProjectFileIndex.getInstance(myProject)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    if (myProject.isDefault) return null

    val vFile = nodePath.resolve()
    if (vFile != null && Comparing.equal(vFile, myIndex.getContentRootForFile(vFile, hideExcludedFiles()))) {
      val module = myIndex.getModuleForFile(vFile, hideExcludedFiles())
      return getNodeForModule(module, nodePath, subtreeRoot)
    }
    return null
  }

  private fun getNodeForModule(module: Module?, nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    var node: ChangesBrowserNode<*>? = myModuleCache[module]
    if (node == null) {
      val policy = DIRECTORY_POLICY.get(subtreeRoot)
      val parent = if (policy != null && !isTopLevel(nodePath)) policy.getParentNodeInternal(nodePath, subtreeRoot) else subtreeRoot

      node = if (module == null) ChangesBrowserNode.create(myProject, PROJECT_ROOT_TAG) else ChangesBrowserModuleNode(module)
      myModel.insertNodeInto(node, parent, parent.childCount)
      myModuleCache[module] = node
    }
    return node
  }

  private fun isTopLevel(nodePath: StaticFilePath): Boolean {
    val parentPath = nodePath.parent
    val parentFile = parentPath?.resolve()

    return parentFile == null || myIndex.getContentRootForFile(parentFile, hideExcludedFiles()) == null
  }

  companion object {

    const val PROJECT_ROOT_TAG = "<Project Root>"

    private fun hideExcludedFiles(): Boolean {
      return Registry.`is`("ide.hide.excluded.files")
    }
  }
}
