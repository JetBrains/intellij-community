// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.rpc.rpcId
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.ActionShortcutProvider
import com.intellij.ide.util.FileStructureFilter
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.FileStructurePopup.getDefaultValue
import com.intellij.ide.util.treeView.smartTree.ProvidingTreeModel
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeModel
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.LocationPresentation
import com.intellij.platform.structureView.impl.DelegatingNodeProvider
import com.intellij.platform.structureView.impl.dto.ColoredFragmentDto
import com.intellij.platform.structureView.impl.dto.PresentationDataDto
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.dto.toDto
import com.intellij.platform.structureView.impl.uiModel.DelegatingProviderTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.FilterTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.NodeProviderTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.SorterTreeActionDto
import com.intellij.platform.structureView.impl.uiModel.StructureTreeActionDto

internal fun StructureViewTreeElement.toDto(id: Int,
                                            parentId: Int,
                                            index: Int,
                                            autoExpand: Boolean?,
                                            alwaysShowsPlus: Boolean?,
                                            alwaysLeaf: Boolean?,
                                            speedSearchText: String?,
                                            filterResults: List<Boolean>): StructureViewTreeElementDto {
  val presentation = presentation
  return StructureViewTreeElementDto(
    id,
    parentId,
    index,
    speedSearchText,
    this.value.hashCode(),
    presentation.toDto(),
    autoExpand ?: false,
    alwaysShowsPlus ?: false,
    alwaysLeaf ?: false,
    filterResults,
  )
}

internal fun ItemPresentation.toDto(): PresentationDataDto {
  return PresentationDataDto(
    getIcon(false)?.rpcId(),
    presentableText,
    locationString,
    ((this as? ColoredItemPresentation)?.textAttributesKey)?.rpcId(),
    (this as? LocationPresentation)?.locationPrefix,
    (this as? LocationPresentation)?.locationSuffix,
    (this as? PresentationData)?.coloredText?.map {
      ColoredFragmentDto(it.text, it.toolTip, it.attributes.rpcId())
    } ?: emptyList()
  )
}

internal fun createActionModels(treeModel: StructureViewModel): List<StructureTreeActionDto> {
  val sorterDtos = treeModel.sorters.toDto()

  val nodeProviders = getNodeProviders(treeModel)

  val nodeProviderDtos = nodeProviders?.map { provider ->
    val (actionIdForShortcut, shortcut) = if (provider is ActionShortcutProvider) {
      provider.actionIdForShortcut to emptyList()
    }
    else {
      null to provider.shortcut.map { it.rpcId() }
    }

    if (provider is DelegatingNodeProvider<*>) {
      DelegatingProviderTreeActionDto(
        StructureTreeActionDto.Type.FILTER,
        provider.name,
        false,
        provider.presentation.toDto(),
        shortcut.toTypedArray(),
        actionIdForShortcut,
        provider.checkBoxText,
        getDefaultValue(provider),
      )
    }
    else {
      NodeProviderTreeActionDto(
        StructureTreeActionDto.Type.FILTER,
        provider.name,
        provider.presentation.toDto(),
        false,
        getDefaultValue(provider),
        shortcut.toTypedArray(),
        actionIdForShortcut,
        provider.checkBoxText,
      )
    }
  } ?: emptyList()

  //todo for not a popup these don't have to implement FileStructureFilter
  val filterDtos = treeModel.filters.filterIsInstance<FileStructureFilter>().mapIndexed { index, filter ->
    val (actionIdForShortcut, shortcut) = if (filter is ActionShortcutProvider) {
      filter.actionIdForShortcut to emptyList()
    }
    else {
      null to filter.shortcut.map { it.rpcId() }
    }

    FilterTreeActionDto(
      index,
      StructureTreeActionDto.Type.FILTER,
      filter.name,
      filter.presentation.toDto(),
      filter.isReverted,
      getDefaultValue(filter),
      shortcut.toTypedArray(),
      actionIdForShortcut,
      filter.checkBoxText,
    )
  }

  return sorterDtos + nodeProviderDtos + filterDtos
}

internal fun getNodeProviders(treeModel: TreeModel): List<FileStructureNodeProvider<*>>? {
  return (treeModel as? ProvidingTreeModel)?.nodeProviders?.filterIsInstance<FileStructureNodeProvider<*>>()
}

private fun Array<Sorter>.toDto(): List<StructureTreeActionDto> {
  val dto = mutableListOf<StructureTreeActionDto>()
  for (sorter in this) {
    if (!sorter.isVisible) continue
    dto.add(SorterTreeActionDto(
      StructureTreeActionDto.Type.SORTER,
      sorter.name,
      false,
      sorter.presentation.toDto(),
      getDefaultValue(sorter),
    ))
  }
  return dto
}