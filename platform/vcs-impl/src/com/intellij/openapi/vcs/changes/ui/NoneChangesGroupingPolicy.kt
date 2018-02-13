// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import javax.swing.tree.DefaultTreeModel

class NoneChangesGroupingPolicy : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? = null

  class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel) = NoneChangesGroupingPolicy()
  }
}