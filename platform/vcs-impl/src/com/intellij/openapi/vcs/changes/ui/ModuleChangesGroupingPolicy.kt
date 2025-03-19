// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcsUtil.VcsImplUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
class ModuleChangesGroupingPolicy(val project: Project, model: DefaultTreeModel) : SimpleChangesGroupingPolicy<Module>(model) {
  private val myIndex = ProjectFileIndex.getInstance(project)

  override fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Module? {
    val file = VcsImplUtil.findValidParentAccurately(nodePath.filePath) ?: return null
    val module = myIndex.getModuleForFile(file, HIDE_EXCLUDED_FILES) ?: return null
    if (ModuleType.isInternal(module)) return null

    return module
  }

  override fun createGroupRootNode(value: Module): ChangesBrowserNode<*>? {
    val moduleNode = ChangesBrowserModuleNode.create(value) ?: return null
    moduleNode.markAsHelperNode()
    return moduleNode
  }

  internal class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) = ModuleChangesGroupingPolicy(project, model)
  }

  companion object {
    private val HIDE_EXCLUDED_FILES: Boolean = Registry.`is`("ide.hide.excluded.files")
  }
}
