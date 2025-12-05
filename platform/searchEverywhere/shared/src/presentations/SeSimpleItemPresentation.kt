// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@Serializable
class SeSimpleItemPresentation(
    val iconId: IconId?,
    val textChunk: SerializableTextChunk,
    val selectedTextChunk: SerializableTextChunk?,
    val description: @NlsSafe String?,
    val accessibleAdditionToText: @NlsSafe String?,
    override val extendedInfo: SeExtendedInfo?,
    override val isMultiSelectionSupported: Boolean,
) : SeItemPresentation {
  override val text: @Nls String get() = textChunk.text

  constructor(
      iconId: IconId?,
      text: @NlsSafe String,
      description: @NlsSafe String?,
      accessibleAdditionToText: @NlsSafe String?,
      extendedInfo: SeExtendedInfo?,
      isMultiSelectionSupported: Boolean,
  ) : this(
    iconId,
      SerializableTextChunk(text),
    null,
    description,
    accessibleAdditionToText,
    extendedInfo,
    isMultiSelectionSupported)

  constructor(text: @NlsSafe String, isMultiSelectionSupported: Boolean) : this(null,
      SerializableTextChunk(text), null, null, null, null, isMultiSelectionSupported)
  constructor(iconId: IconId?,
              text: @NlsSafe String,
              extendedInfo: SeExtendedInfo?,
              isMultiSelectionSupported: Boolean) : this(iconId,
      SerializableTextChunk(text), null, null, null, extendedInfo, isMultiSelectionSupported)

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeSimpleItemPresentation) return false

    return super.contentEquals(other) &&
           selectedTextChunk?.text == other.selectedTextChunk?.text &&
           description == other.description &&
           accessibleAdditionToText == other.accessibleAdditionToText
  }
}