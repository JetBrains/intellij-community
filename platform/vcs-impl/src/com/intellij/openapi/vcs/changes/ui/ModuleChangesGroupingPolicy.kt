// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.ui.ChangesModuleGroupingPolicy.Companion.HIDE_EXCLUDED_FILES
import com.intellij.openapi.vcs.changes.ui.ChangesModuleGroupingPolicy.Companion.MODULE_CACHE
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.*
import javax.swing.tree.DefaultTreeModel

class ModuleChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  private val myIndex = ProjectFileIndex.getInstance(project)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val file = resolveVirtualFile(nodePath)
    val nextPolicyParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot)

    file?.let { myIndex.getModuleForFile(file, HIDE_EXCLUDED_FILES) }?.let { module ->
      val grandParent = nextPolicyParent ?: subtreeRoot
      val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

      MODULE_CACHE.getValue(cachingRoot)[module]?.let { return it }

      ChangesBrowserModuleNode(module).let {
        it.markAsHelperNode()

        model.insertNodeInto(it, grandParent, grandParent.childCount)

        MODULE_CACHE.getValue(cachingRoot)[module] = it
        DIRECTORY_CACHE.getValue(cachingRoot)[staticFrom(it.moduleRoot).key] = it
        IS_CACHING_ROOT.set(it, true)
        MODULE_CACHE.getValue(it)[module] = it
        DIRECTORY_CACHE.getValue(it)[staticFrom(it.moduleRoot).key] = it
        return it
      }
    }

    return nextPolicyParent
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel): ModuleChangesGroupingPolicy = ModuleChangesGroupingPolicy(project, model)
  }
}