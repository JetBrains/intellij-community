// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.ide.rpc.ShortcutId
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
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

  @Volatile
  private var myNodesByParentId: Map<Int, List<StructureUiTreeElement>> = emptyMap()

  @Volatile
  var nodesLoaded: Boolean = false
    private set

  fun setNodes(newNodes: List<StructureUiTreeElement>) {
    myNodesByParentId = newNodes.groupBy { node ->
      (node as? StructureUiTreeElementImpl)?.dto?.parentId ?: -1
    }
    nodesLoaded = true
  }

  fun getNodes(parent: StructureUiTreeElement): List<StructureUiTreeElement> {
    return myNodesByParentId[parent.id] ?: emptyList()
  }
}
