// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.ide.rpc.ShortcutId
import com.intellij.ide.rpc.shortcut
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.platform.structureView.frontend.uiModel.StructureTreeAction.Companion.fromDto
import com.intellij.platform.structureView.impl.dto.toPresentation
import com.intellij.platform.structureView.impl.uiModel.DelegatingProviderTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.FilterTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.NodeProviderTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.SorterTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.StructureTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
interface StructureTreeAction {
  val actionType: Type
  val name: @NonNls String
  val isReverted: Boolean
  val presentation: ActionPresentation
  val isEnabledByDefault: Boolean

  enum class Type {
    GROUP,
    SORTER,
    FILTER
  }

  companion object {
    fun StructureTreeActionDto.Type.fromDto(): Type = when (this) {
      StructureTreeActionDto.Type.GROUP -> Type.GROUP
      StructureTreeActionDto.Type.SORTER -> Type.SORTER
      StructureTreeActionDto.Type.FILTER -> Type.FILTER
    }
  }
}

@ApiStatus.NonExtendable
interface CheckboxTreeAction : StructureTreeAction {
  val shortcutsIds: Array<ShortcutId>?
  val actionIdForShortcut: String?
  val checkboxText: @Nls String

  val shortcuts: Array<Shortcut>?
    get() = shortcutsIds?.mapNotNull { it.shortcut() }?.toTypedArray()
}

class SorterTreeAction(
  override val actionType: StructureTreeAction.Type,
  override val name: String,
  override val isReverted: Boolean,
  override val presentation: ActionPresentation,
  override val isEnabledByDefault: Boolean,
) : StructureTreeAction

class DelegatingProviderTreeAction(
  override val actionType: StructureTreeAction.Type = StructureTreeAction.Type.FILTER,
  override val name: @NonNls String,
  override val isReverted: Boolean,
  override val presentation: ActionPresentation,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
  override val isEnabledByDefault: Boolean,
) : CheckboxTreeAction

class FilterTreeAction(
  val order: Int,
  override val actionType: StructureTreeAction.Type,
  override val name: String,
  override val presentation: ActionPresentation,
  override val isReverted: Boolean,
  override val isEnabledByDefault: Boolean,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
) : CheckboxTreeAction {

  fun isVisible(element: StructureUiTreeElement): Boolean {
    return element.filterResults.getOrNull(order) ?: true
  }
}


fun StructureTreeActionDto.toImpl(): StructureTreeAction = when (this) {
  is SorterTreeActionDto -> {
    SorterTreeAction(actionType.fromDto(),
                     name,
                     isReverted,
                     presentationDto.toPresentation(),
                     isEnabledByDefault)
  }
  is DelegatingProviderTreeActionDto -> {
    DelegatingProviderTreeAction(actionType.fromDto(),
                                 name,
                                 isReverted,
                                 presentationDto.toPresentation(),
                                 shortcutsIds,
                                 actionIdForShortcut,
                                 checkboxText,
                                 isEnabledByDefault)
  }
  is FilterTreeActionDto -> {
    FilterTreeAction(order,
                     actionType.fromDto(),
                     name,
                     presentationDto.toPresentation(),
                     isReverted,
                     isEnabledByDefault,
                     shortcutsIds,
                     actionIdForShortcut,
                     checkboxText)
  }
  is NodeProviderTreeActionDto -> {
    NodeProviderTreeAction(actionType.fromDto(),
                           name,
                           presentationDto.toPresentation(),
                           isReverted,
                           isEnabledByDefault,
                           shortcutsIds,
                           actionIdForShortcut,
                           checkboxText)
  }
}
