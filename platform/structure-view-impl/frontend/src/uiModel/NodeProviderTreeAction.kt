// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.ide.rpc.ShortcutId
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls

class NodeProviderTreeAction(
  override val actionType: StructureTreeAction.Type,
  override val name: String,
  override val presentation: ActionPresentation,
  override val isReverted: Boolean,
  override val isEnabledByDefault: Boolean,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
) : CheckboxTreeAction {

  private var myNodesByParentId: Map<Int, List<StructureViewNode>> = emptyMap()

  @all:RequiresEdt
  var nodesLoaded: Boolean = false
    private set

  @RequiresEdt
  internal fun setNodesByParentId(nodesByParentId: Map<Int, List<StructureViewNode>>) {
    myNodesByParentId = nodesByParentId
    nodesLoaded = true
  }

  @RequiresEdt
  internal fun getNodes(parent: StructureUiTreeElement): List<StructureViewNode> {
    return myNodesByParentId[parent.id] ?: emptyList()
  }
}
