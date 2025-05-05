// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.SimpleTextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Experimental
@Serializable
sealed interface SeItemPresentation {
  val text: String
}

@ApiStatus.Internal
@Serializable
class SeSimpleItemPresentation(
  val iconId: IconId? = null,
  val textChunk: SerializableTextChunk? = null,
  val selectedTextChunk: SerializableTextChunk? = null,
  val description: @NlsSafe String? = null) : SeItemPresentation {
    override val text: @Nls String get() = textChunk?.text ?: ""

  constructor(iconId: IconId? = null, text: @NlsSafe String? = null, description: @NlsSafe String? = null) : this(
    iconId,
    text?.let { SerializableTextChunk(it, null, 0) },
    null,
    description)
  }

@ApiStatus.Internal
sealed interface SeActionItemPresentation : SeItemPresentation {
  val commonData: Common

  @ApiStatus.Internal
  @Serializable
  data class Common(
    val text: @Nls String,
    val location: @Nls String? = null,
    private var _switcherState: Boolean? = null,
  ) {
    val switcherState: Boolean? get() = _switcherState
    fun toggleStateIfSwitcher() {
      _switcherState = _switcherState?.not()
    }
  }
}

@ApiStatus.Internal
@Serializable
data class SeRunnableActionItemPresentation(
  override val commonData: SeActionItemPresentation.Common,
  val toolTip: @Nls String? = null,
  val actionId: @Nls String? = null,
  val isEnabled: Boolean = true,
  val shortcut: @NlsSafe String? = null,
  val promo: Promo? = null,
  val iconId: IconId? = null,
  val selectedIconId: IconId? = null,
) : SeActionItemPresentation {
  override val text: String get() = commonData.text

  @ApiStatus.Internal
  @Serializable
  data class Promo(
    val productIconId: IconId?,
    val callToActionText: @Nls String,
  )
}

@ApiStatus.Internal
@Serializable
data class SeOptionActionItemPresentation(
  override val commonData: SeActionItemPresentation.Common,
  val value: @NlsSafe String? = null,
  val isBooleanOption: Boolean = false,
) : SeActionItemPresentation {
  override val text: String get() = commonData.text
}

@ApiStatus.Internal
@Serializable
class SeTargetItemPresentation(
  private val backgroundColorId: ColorId?,
  private val iconId: IconId?,
  val presentableText: @Nls String,
  val presentableTextMatchedRanges: List<SerializableRange>?,
  private val presentableTextFgColorId: ColorId?,
  val containerText: @Nls String?,
  val containerTextMatchedRanges: List<SerializableRange>?,
  val locationText: @Nls String?,
  private val locationIconId: IconId?,
) : SeItemPresentation {
  override val text: String get() = presentableText

  val backgroundColor: Color? get() = backgroundColorId?.color()
  val icon: Icon? get() = iconId?.icon()
  val locationIcon: Icon? get() = locationIconId?.icon()
  val presentableTextFgColor: Color? get() = presentableTextFgColorId?.color()

  @Serializable
  data class SerializableRange(val start: Int, val end: Int) {
    val textRange: TextRange get() = TextRange(start, end)

    constructor(textRange: TextRange) : this(textRange.startOffset, textRange.endOffset)
  }

  companion object {
    fun create(tp: TargetPresentation, matchers: ItemMatchers?): SeTargetItemPresentation =
      SeTargetItemPresentation(backgroundColorId = tp.backgroundColor?.rpcId(),
                               iconId = tp.icon?.rpcId(),
                               presentableText = tp.presentableText,
                               presentableTextMatchedRanges = matchers?.calcMatchedRanges(tp.presentableText),
                               presentableTextFgColorId = tp.presentableTextAttributes?.foregroundColor?.rpcId(),
                               containerText = tp.containerText,
                               containerTextMatchedRanges = matchers?.calcMatchedRanges(tp.containerText),
                               locationText = tp.locationText,
                               locationIconId = tp.locationIcon?.rpcId())

    private fun ItemMatchers.calcMatchedRanges(text: String?): List<SerializableRange>? {
      text ?: return null
      return (nameMatcher as? MinusculeMatcher)?.matchingFragments(text)?.map { SerializableRange(it) }
    }
  }
}

@ApiStatus.Internal
@Serializable
class SeTextSearchItemPresentation(
  override val text: @NlsSafe String,
  val textChunks: List<SerializableTextChunk>,
  private val backgroundColorId: ColorId?,
  val fileString: @NlsSafe String,
) : SeItemPresentation {
  val backgroundColor: Color? get() = backgroundColorId?.color()
}

@ApiStatus.Internal
@Serializable
class SerializableTextChunk(val text: @NlsSafe String, val foregroundColorId: ColorId?, val fontType: Int) {
  @Suppress("USELESS_CAST")
  constructor(text: @NlsSafe String, attributes: SimpleTextAttributes) :
    this(text, (attributes.fgColor as? Color)?.rpcId(), attributes.fontStyle)
}
