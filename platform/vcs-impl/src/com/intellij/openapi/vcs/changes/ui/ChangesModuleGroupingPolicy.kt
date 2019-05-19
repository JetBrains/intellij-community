// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultTreeModel

@Deprecated("Use ChangesGroupingPolicyFactory instead")
open class ChangesModuleGroupingPolicy(val myProject: Project, val myModel: DefaultTreeModel) : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    return null
  }
}