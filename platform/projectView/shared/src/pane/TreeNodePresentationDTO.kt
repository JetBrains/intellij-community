// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeTextFragment
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
fun TreeNodePresentation.toDTO(): TreeNodePresentationDTO = (this as TreeNodePresentationImpl).toDTO()

@ApiStatus.Internal
fun TreeNodePresentationImpl.toDTO(): TreeNodePresentationDTO = TreeNodePresentationDTO(
  isLeaf = isLeaf,
  icon = icon?.let { serializeToRpc(it) },
  mainText = mainText,
  fullText = fullText.map { it.toDTO() },
  toolTip = toolTip,
)

@ApiStatus.Internal
fun TreeNodePresentationDTO.toPresentation(): TreeNodePresentation = TreeNodePresentationImpl(
  isLeaf = isLeaf,
  icon = deserializeFromRpc(icon, Icon ::class),
  mainText = mainText,
  fullText = fullText.map { it.toTextFragment() },
  toolTip = toolTip,
)

@ApiStatus.Internal
@Serializable
data class TreeNodePresentationDTO(
  val isLeaf: Boolean,
  val icon: SerializedValue?,
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
  bgColor?.let { serializeToRpc(it) },
  fgColor?.let { serializeToRpc(it) },
  waveColor?.let { serializeToRpc(it) },
  style,
)

@ApiStatus.Internal
fun SimpleTextAttributesDTO.toAttributes(): SimpleTextAttributes = SimpleTextAttributes(
  bgColor?.let { deserializeFromRpc(it, Color::class) },
  fgColor?.let { deserializeFromRpc(it, Color::class) },
  waveColor?.let { deserializeFromRpc(it, Color::class) },
  style,
)

@ApiStatus.Internal
@Serializable
data class SimpleTextAttributesDTO(
  val bgColor: SerializedValue?,
  val fgColor: SerializedValue?,
  val waveColor: SerializedValue?,
  val style: Int,
)
