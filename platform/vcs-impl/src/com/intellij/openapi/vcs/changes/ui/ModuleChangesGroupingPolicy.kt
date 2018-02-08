// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultTreeModel

class ModuleChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : ChangesGroupingPolicy {
  private val innerPolicy = ChangesModuleGroupingPolicy(project, model)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    generateSequence(nodePath) { it.parent }.forEach { path ->
      innerPolicy.getParentNodeFor(path, subtreeRoot)?.let {
        it.markAsHelperNode()
        return it
      }
    }
    return null
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel) = ModuleChangesGroupingPolicy(project, model)
  }
}