// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl.dto

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.ui.colors.SerializableSimpleTextAttributes
import com.intellij.ide.ui.colors.TextAttributeKeyId
import com.intellij.ide.ui.colors.attributes
import com.intellij.ide.ui.colors.key
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
@Serializable
data class PresentationDataDto(
  val icon: IconId?,
  val presentableText: @Nls String?,
  val locationString: @Nls String?,
  val textAttributesKey: TextAttributeKeyId?,
  val locationPrefix: @Nls String?,
  val locationSuffix: @Nls String?,
  val coloredText: List<ColoredFragmentDto>,
)

@Internal
@Serializable
data class ColoredFragmentDto(
  val text: @Nls String,
  val tooltip: @Nls String?,
  val attributes: SerializableSimpleTextAttributes,
)

fun PresentationDataDto.toPresentation(): PresentationData {
  return PresentationData(
    presentableText,
    locationString,
    icon?.icon(),
    textAttributesKey?.key()
  ).also { data ->
    data.coloredText.addAll(coloredText.map {
      PresentableNodeDescriptor.ColoredFragment(it.text, it.tooltip, it.attributes.attributes())
    })
    data.setLocationSuffix(locationSuffix)
    data.setLocationPrefix(locationPrefix)
  }
}