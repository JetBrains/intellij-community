// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeTextFragment
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun TreeNodePresentation.toDTO(): TreeNodePresentationDTO = (this as TreeNodePresentationImpl).toDTO()

@ApiStatus.Internal
fun TreeNodePresentationImpl.toDTO(): TreeNodePresentationDTO = TreeNodePresentationDTO(
  isLeaf = isLeaf,
  iconId = icon?.rpcId(),
  mainText = mainText,
  fullText = fullText.map { it.toDTO() },
  toolTip = toolTip,
)

@ApiStatus.Internal
fun TreeNodePresentationDTO.toPresentation(): TreeNodePresentation = TreeNodePresentationImpl(
  isLeaf = isLeaf,
  icon = iconId?.icon(),
  mainText = mainText,
  fullText = fullText.map { it.toTextFragment() },
  toolTip = toolTip,
)

@ApiStatus.Internal
@Serializable
data class TreeNodePresentationDTO(
  val isLeaf: Boolean,
  val iconId: IconId?,
  val mainText: String,
  val fullText: List<TreeNodeTextFragmentDTO>,
  val toolTip: String?,
)

@ApiStatus.Internal
fun TreeNodeTextFragment.toDTO(): TreeNodeTextFragmentDTO = TreeNodeTextFragmentDTO(
  text = text,
  attributesDTO = attributes.toDTO(),
)

@ApiStatus.Internal
fun TreeNodeTextFragmentDTO.toTextFragment(): TreeNodeTextFragment = TreeNodeTextFragment(
  text = text,
  attributes = attributesDTO.toAttributes(),
)

@ApiStatus.Internal
@Serializable
data class TreeNodeTextFragmentDTO(
  val text: String,
  val attributesDTO: SimpleTextAttributesDTO,
)

@ApiStatus.Internal
fun SimpleTextAttributes.toDTO(): SimpleTextAttributesDTO = SimpleTextAttributesDTO(
  bgColor?.rpcId(),
  fgColor?.rpcId(),
  waveColor?.rpcId(),
  style,
)

@ApiStatus.Internal
fun SimpleTextAttributesDTO.toAttributes(): SimpleTextAttributes = SimpleTextAttributes(
  bgColorId?.color(),
  fgColorId?.color(),
  waveColorId?.color(),
  style,
)

@ApiStatus.Internal
@Serializable
data class SimpleTextAttributesDTO(
  val bgColorId: ColorId?,
  val fgColorId: ColorId?,
  val waveColorId: ColorId?,
  val style: Int,
)
