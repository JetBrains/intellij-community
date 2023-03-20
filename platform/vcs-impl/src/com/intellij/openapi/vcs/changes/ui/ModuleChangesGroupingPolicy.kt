// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.*
import javax.swing.tree.DefaultTreeModel

class ModuleChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  private val myIndex = ProjectFileIndex.getInstance(project)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val file = resolveVirtualFile(nodePath)
    val nextPolicyParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot)

    file?.let { myIndex.getModuleForFile(file, HIDE_EXCLUDED_FILES) }?.let { module ->
      if (ModuleType.isInternal(module)) return nextPolicyParent

      val grandParent = nextPolicyParent ?: subtreeRoot
      val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

      MODULE_CACHE.getValue(cachingRoot)[module]?.let { return it }

      val moduleNode = ChangesBrowserModuleNode.create(module)
      if (moduleNode == null) return nextPolicyParent

      moduleNode.let {
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

  internal class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) = ModuleChangesGroupingPolicy(project, model)
  }

  companion object {
    private val MODULE_CACHE: NotNullLazyKey<MutableMap<Module?, ChangesBrowserNode<*>>, ChangesBrowserNode<*>> =
      NotNullLazyKey.createLazyKey("ChangesTree.ModuleCache") { mutableMapOf() }
    private val HIDE_EXCLUDED_FILES: Boolean = Registry.`is`("ide.hide.excluded.files")
  }
}