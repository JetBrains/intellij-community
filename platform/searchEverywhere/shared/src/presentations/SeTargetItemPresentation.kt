// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

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
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

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
  override val extendedInfo: SeExtendedInfo?,
  override val isMultiSelectionSupported: Boolean,
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
    fun create(tp: TargetPresentation, matchers: ItemMatchers?, extendedInfo: SeExtendedInfo?, isMultiSelectionSupported: Boolean): SeTargetItemPresentation =
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
                               extendedInfo = extendedInfo,
                               isMultiSelectionSupported = isMultiSelectionSupported)

    private fun MinusculeMatcher.calcMatchedRanges(text: String?): List<SerializableRange>? {
      text ?: return null
      return matchingFragments(text)?.map { SerializableRange(it) }
    }
  }

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeTargetItemPresentation) return false
    return super.contentEquals(other) &&
           presentableText == other.presentableText &&
           containerText == other.containerText &&
           locationText == other.locationText
  }
}
