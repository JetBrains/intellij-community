// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.JBColor
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
  val extendedDescription: String? get() = null
}

@ApiStatus.Internal
@Serializable
class SeSimpleItemPresentation(
  val iconId: IconId? = null,
  val textChunk: SerializableTextChunk? = null,
  val selectedTextChunk: SerializableTextChunk? = null,
  val description: @NlsSafe String? = null,
  val accessibleAdditionToText: @NlsSafe String? = null,
  override val extendedDescription: String? = null,
) : SeItemPresentation {
  override val text: @Nls String get() = textChunk?.text ?: ""

  constructor(
    iconId: IconId? = null,
    text: @NlsSafe String? = null,
    description: @NlsSafe String? = null,
    accessibleAdditionToText: @NlsSafe String? = null,
    extendedDescription: String? = null,
  ) : this(
    iconId,
    text?.let { SerializableTextChunk(it) },
    null,
    description,
    accessibleAdditionToText,
    extendedDescription)
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
    val extendedDescription: String? = null,
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
  override val extendedDescription: String? get() = commonData.extendedDescription

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
  override val extendedDescription: String? get() = commonData.extendedDescription
}

@ApiStatus.Internal
@Serializable
class SeTargetItemPresentation(
  private val backgroundColorId: ColorId? = null,
  private val iconId: IconId? = null,
  val presentableText: @NlsSafe String,
  val presentableTextMatchedRanges: List<SerializableRange>? = null,
  private val presentableTextFgColorId: ColorId? = null,
  val presentableTextErrorHighlight: Boolean = false,
  val presentableTextStrikethrough: Boolean = false,
  val containerText: @NlsSafe String? = null,
  val containerTextMatchedRanges: List<SerializableRange>? = null,
  val locationText: @NlsSafe String? = null,
  private val locationIconId: IconId? = null,
  override val extendedDescription: @NlsSafe String? = null,
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
    fun create(tp: TargetPresentation, matchers: ItemMatchers?, extendedDescription: String?): SeTargetItemPresentation =
      SeTargetItemPresentation(backgroundColorId = tp.backgroundColor?.rpcId(),
                               iconId = tp.icon?.rpcId(),
                               presentableText = tp.presentableText,
                               presentableTextMatchedRanges = (matchers?.nameMatcher as? MinusculeMatcher)?.calcMatchedRanges(tp.presentableText),
                               presentableTextFgColorId = tp.presentableTextAttributes?.foregroundColor?.rpcId(),
                               presentableTextErrorHighlight = tp.presentableTextAttributes?.let { attrs ->
                                 val simpleAttrs = SimpleTextAttributes.fromTextAttributes(attrs)
                                 simpleAttrs.isWaved && attrs.effectColor == JBColor.RED
                               } == true,
                               presentableTextStrikethrough = tp.presentableTextAttributes?.let { attrs ->
                                 SimpleTextAttributes.fromTextAttributes(attrs).isStrikeout ||
                                 attrs.additionalEffects?.contains(EffectType.STRIKEOUT) == true
                               } == true,
                               containerText = tp.containerText,
                               containerTextMatchedRanges = (matchers?.locationMatcher as? MinusculeMatcher)?.calcMatchedRanges(tp.containerText),
                               locationText = tp.locationText,
                               locationIconId = tp.locationIcon?.rpcId(),
                               extendedDescription = extendedDescription)

    private fun MinusculeMatcher.calcMatchedRanges(text: String?): List<SerializableRange>? {
      text ?: return null
      return matchingFragments(text)?.map { SerializableRange(it) }
    }
  }
}

@ApiStatus.Internal
@Serializable
class SeTextSearchItemPresentation(
  override val text: @NlsSafe String,
  override val extendedDescription: @NlsSafe String?,
  val textChunks: List<SerializableTextChunk>,
  private val backgroundColorId: ColorId?,
  val fileString: @NlsSafe String,
) : SeItemPresentation {
  val backgroundColor: Color? get() = backgroundColorId?.color()
}
