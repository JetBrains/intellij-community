// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl.uiModel

import com.intellij.ide.rpc.ShortcutId
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.dto.TreeActionPresentationDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface StructureTreeActionDto {
  val actionType: Type
  val name: @NonNls String
  val isReverted: Boolean
  val presentationDto: TreeActionPresentationDto
  val isEnabledByDefault: Boolean

  enum class Type {
    GROUP,
    SORTER,
    FILTER;
  }
}

@ApiStatus.Internal
@Serializable
sealed interface CheckboxTreeActionDto : StructureTreeActionDto {
  val shortcutsIds: Array<ShortcutId>?
  val actionIdForShortcut: String?
  val checkboxText: @Nls String
}

@Serializable
class SorterTreeActionDto(
  override val actionType: StructureTreeActionDto.Type,
  override val name: String,
  override val isReverted: Boolean,
  override val presentationDto: TreeActionPresentationDto,
  override val isEnabledByDefault: Boolean,
) : StructureTreeActionDto

@Serializable
class DelegatingProviderTreeActionDto(
  override val actionType: StructureTreeActionDto.Type,
  override val name: @NonNls String,
  override val isReverted: Boolean,
  override val presentationDto: TreeActionPresentationDto,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
  override val isEnabledByDefault: Boolean,
) : CheckboxTreeActionDto

@Serializable
class NodeProviderTreeActionDto(
  override val actionType: StructureTreeActionDto.Type,
  override val name: String,
  override val presentationDto: TreeActionPresentationDto,
  override val isReverted: Boolean,
  override val isEnabledByDefault: Boolean,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
) : CheckboxTreeActionDto

@Serializable
class FilterTreeActionDto(
  val order: Int,
  override val actionType: StructureTreeActionDto.Type,
  override val name: String,
  override val presentationDto: TreeActionPresentationDto,
  override val isReverted: Boolean,
  override val isEnabledByDefault: Boolean,
  override val shortcutsIds: Array<ShortcutId>?,
  override val actionIdForShortcut: String?,
  override val checkboxText: @Nls String,
) : CheckboxTreeActionDto