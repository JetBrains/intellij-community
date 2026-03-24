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
  private var myNodes: List<StructureUiTreeElement> = emptyList()

  @Volatile
  var nodesLoaded: Boolean = false
    private set

  val nodes: List<StructureUiTreeElement>
    get() = myNodes

  fun setNodes(newNodes: List<StructureUiTreeElement>) {
    myNodes = newNodes
    nodesLoaded = true
  }

  fun getNodes(parent: StructureUiTreeElement): List<StructureUiTreeElement> {
    return nodes.filter {
      if (it !is StructureUiTreeElementImpl) return@filter false
      it.dto.parentId == parent.id
    }
  }
}
