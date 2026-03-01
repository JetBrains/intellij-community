// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.structureView.newStructureView.TreeActionsOwner
import com.intellij.ide.structureView.newStructureView.TreeActionsOwnerEx
import com.intellij.ide.util.FileStructurePopup.getDefaultValue
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.ide.util.treeView.smartTree.TreeAction
import com.intellij.platform.structureView.impl.DelegatingNodeProvider

internal class BackendTreeActionOwner(
  var allNodeProvidersActive: Boolean = false,
) : TreeActionsOwner, TreeActionsOwnerEx {

  // Stores state only for autoclicked actions (not user-initiated)
  private val autoclickedActions = hashMapOf<String, Boolean>()

  override fun setActionActive(name: String?, state: Boolean) {}

  override fun isActionActive(name: String?): Boolean = false

  override fun setActionActive(action: TreeAction, state: Boolean) {}

  fun setAutoclickedActionState(name: String, state: Boolean) {
    autoclickedActions[name] = state
  }

  fun clearAutoclickedActionState(name: String) {
    autoclickedActions.remove(name)
  }

  override fun isActionActive(action: TreeAction): Boolean {
    // always false for filters so that the elements are not filtered out
    if (action is Filter) return false

    // for node providers, check flag first
    if (action is NodeProvider<*> && action !is DelegatingNodeProvider<*>) {
      if (allNodeProvidersActive) return true
    }

    // check autoclicked state first, then fall back to default value
    return autoclickedActions[action.name] ?: getDefaultValue(action)
  }
}