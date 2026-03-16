// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
@Serializable
class SeTextSearchItemPresentation(
    override val text: @NlsSafe String,
    override val extendedInfo: SeExtendedInfo?,
    val textChunks: List<SerializableTextChunk>,
    private val backgroundColorId: ColorId?,
    val fileString: @NlsSafe String,
    override val isMultiSelectionSupported: Boolean,
) : SeItemPresentation {
  val backgroundColor: Color? get() = backgroundColorId?.color()

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeTextSearchItemPresentation) return false

    return super.contentEquals(other) &&
           fileString == other.fileString &&
           isMultiSelectionSupported == other.isMultiSelectionSupported
  }
}