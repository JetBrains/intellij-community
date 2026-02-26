// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.ide.rpc.util.TextRangeDto
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.matching.MatchedFragment
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

/**
 * Represents a base interface for serializable target item (file, class, psi element) presentations in the "Search Everywhere".
 */
@Serializable
@ApiStatus.Experimental
sealed interface SeTargetItemPresentation : SeItemPresentation

/**
 * Builder class for constructing instances of `SeTargetItemPresentation`.
 * This class provides methods to customize the properties of a target item presentation used in "Search Everywhere".
 *
 * Once all desired properties are set, the `build` method can be used to create an immutable
 * instance of `SeTargetItemPresentation` that encapsulates the specified configuration.
 * Has a method which directly constructs a `SeTargetItemPresentation` instance from [TargetPresentation]
 * which is used in some of the legacy [com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor].
 */
@ApiStatus.Experimental
class SeTargetItemPresentationBuilder {
  private var backgroundColorId: ColorId? = null
  private var iconId: IconId? = null
  private var iconOriginalWidth: Int? = null
  private var presentableText: String = ""
  private var presentableTextMatchedRanges: List<TextRangeDto>? = null
  private var presentableTextFgColorId: ColorId? = null
  private var presentableTextErrorHighlight: Boolean = false
  private var presentableTextStrikethrough: Boolean = false
  private var containerText: String? = null
  private var containerTextMatchedRanges: List<TextRangeDto>? = null
  private var locationText: String? = null
  private var locationIconId: IconId? = null
  private var locationIconOriginalWidth: Int? = null
  private var extendedInfo: SeExtendedInfo? = null
  private var isMultiSelectionSupported: Boolean = false

  fun withBackgroundColor(color: Color?): SeTargetItemPresentationBuilder {
    this.backgroundColorId = color?.rpcId()
    return this
  }

  fun withIcon(icon: Icon?): SeTargetItemPresentationBuilder {
    this.iconId = icon?.rpcId()
    iconOriginalWidth = icon?.iconWidth
    return this
  }

  fun withPresentableText(text: String): SeTargetItemPresentationBuilder {
    this.presentableText = text
    return this
  }

  fun withPresentableTextMatchedRanges(ranges: List<MatchedFragment>?): SeTargetItemPresentationBuilder {
    this.presentableTextMatchedRanges = ranges?.map { TextRangeDto(it.startOffset, it.endOffset) }
    return this
  }

  fun withPresentableTextFgColor(color: Color?): SeTargetItemPresentationBuilder {
    this.presentableTextFgColorId = color?.rpcId()
    return this
  }

  fun withPresentableTextErrorHighlight(highlight: Boolean): SeTargetItemPresentationBuilder {
    this.presentableTextErrorHighlight = highlight
    return this
  }

  fun withPresentableTextStrikethrough(strikethrough: Boolean): SeTargetItemPresentationBuilder {
    this.presentableTextStrikethrough = strikethrough
    return this
  }

  fun withContainerText(text: String?): SeTargetItemPresentationBuilder {
    this.containerText = text
    return this
  }

  fun withContainerTextMatchedRanges(ranges: List<MatchedFragment>?): SeTargetItemPresentationBuilder {
    this.containerTextMatchedRanges = ranges?.map { TextRangeDto(it.startOffset, it.endOffset) }
    return this
  }

  fun withLocationText(text: String?): SeTargetItemPresentationBuilder {
    this.locationText = text
    return this
  }

  fun locationIcon(icon: Icon?): SeTargetItemPresentationBuilder {
    this.locationIconId = icon?.rpcId()
    locationIconOriginalWidth = icon?.iconWidth
    return this
  }

  fun withExtendedInfo(info: SeExtendedInfo?): SeTargetItemPresentationBuilder {
    this.extendedInfo = info
    return this
  }

  fun withMultiSelectionSupported(supported: Boolean): SeTargetItemPresentationBuilder {
    this.isMultiSelectionSupported = supported
    return this
  }

  fun withTargetPresentation(tp: TargetPresentation, matchers: ItemMatchers?, extendedInfo: SeExtendedInfo?, isMultiSelectionSupported: Boolean): SeTargetItemPresentationBuilder =
    withBackgroundColor(tp.backgroundColor)
      .withIcon(tp.icon)
      .withPresentableText(tp.presentableText)
      .withPresentableTextMatchedRanges((matchers?.nameMatcher as? MinusculeMatcher)?.calcMatchedRanges(tp.presentableText))
      .withPresentableTextFgColor(tp.presentableTextAttributes?.foregroundColor)
      .withPresentableTextErrorHighlight(tp.presentableTextAttributes?.let { attrs ->
        val simpleAttrs = SimpleTextAttributes.fromTextAttributes(attrs)
        simpleAttrs.isWaved && attrs.effectColor == JBColor.RED
      } == true)
      .withPresentableTextStrikethrough(tp.presentableTextAttributes?.let { attrs ->
        SimpleTextAttributes.fromTextAttributes(attrs).isStrikeout ||
        attrs.additionalEffects?.contains(EffectType.STRIKEOUT) == true
      } == true)
      .withContainerText(tp.containerText)
      .withContainerTextMatchedRanges((matchers?.locationMatcher as? MinusculeMatcher)?.calcMatchedRanges(tp.containerText))
      .withLocationText(tp.locationText)
      .locationIcon(tp.locationIcon)
      .withExtendedInfo(extendedInfo)
      .withMultiSelectionSupported(isMultiSelectionSupported)

  fun build(): SeTargetItemPresentation =
    SeTargetItemPresentationImpl(
      backgroundColorId = backgroundColorId,
      iconId = iconId,
      iconOriginalWidth = iconOriginalWidth,
      presentableText = presentableText,
      presentableTextMatchedRanges = presentableTextMatchedRanges,
      presentableTextFgColorId = presentableTextFgColorId,
      presentableTextErrorHighlight = presentableTextErrorHighlight,
      presentableTextStrikethrough = presentableTextStrikethrough,
      containerText = containerText,
      containerTextMatchedRanges = containerTextMatchedRanges,
      locationText = locationText,
      locationIconId = locationIconId,
      locationIconOriginalWidth = locationIconOriginalWidth,
      extendedInfo = extendedInfo,
      isMultiSelectionSupported = isMultiSelectionSupported
    )

  companion object {
    private fun MinusculeMatcher.calcMatchedRanges(text: String?): List<MatchedFragment>? {
      text ?: return null
      return match(text)
    }
  }
}

@Serializable
@ApiStatus.Internal
data class SeTargetItemPresentationImpl(
  private val backgroundColorId: ColorId? = null,
  private val iconId: IconId? = null,
  val iconOriginalWidth: Int? = null,
  val presentableText: @NlsSafe String,
  val presentableTextMatchedRanges: List<TextRangeDto>? = null,
  private val presentableTextFgColorId: ColorId? = null,
  val presentableTextErrorHighlight: Boolean = false,
  val presentableTextStrikethrough: Boolean = false,
  val containerText: @NlsSafe String? = null,
  val containerTextMatchedRanges: List<TextRangeDto>? = null,
  val locationText: @NlsSafe String? = null,
  private val locationIconId: IconId? = null,
  val locationIconOriginalWidth: Int? = null,
  override val extendedInfo: SeExtendedInfo?,
  override val isMultiSelectionSupported: Boolean,
) : SeTargetItemPresentation {
  override val text: String get() = presentableText

  val backgroundColor: Color? get() = backgroundColorId?.color()
  val icon: Icon? get() = iconId?.icon()
  val locationIcon: Icon? get() = locationIconId?.icon()
  val presentableTextFgColor: Color? get() = presentableTextFgColorId?.color()

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeTargetItemPresentationImpl) return false
    return super.contentEquals(other) &&
           presentableText == other.presentableText &&
           containerText == other.containerText &&
           locationText == other.locationText
  }
}
