// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.ui.SimpleTextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents a base interface for serializable basic item presentations in the "Search Everywhere".
 */
@ApiStatus.Experimental
sealed interface SeBasicItemPresentation : SeItemPresentation

/**
 * Builder class for constructing instances of `SeBasicItemPresentation`.
 * This class provides methods to customize the properties of an item presentation used in "Search Everywhere".
 *
 * Once all desired properties are set, the `build` method can be used to create an immutable
 * instance of `SeBasicItemPresentation` that encapsulates the specified configuration.
 */
@ApiStatus.Experimental
class SeBasicItemPresentationBuilder {
  private var iconId: IconId? = null
  private var text: String? = null
  private var textAttributes: SimpleTextAttributes? = null
  private var selectedTextAttributes: SimpleTextAttributes? = null
  private var description: String? = null
  private var accessibleAdditionToText: String? = null
  private var extendedInfo: SeExtendedInfo? = null
  private var isMultiSelectionSupported: Boolean = false

  fun withIcon(icon: Icon?): SeBasicItemPresentationBuilder {
    this.iconId = icon?.rpcId()
    return this
  }

  fun withText(text: @NlsSafe String): SeBasicItemPresentationBuilder {
    this.text = text
    return this
  }

  fun withTextAttributes(attributes: SimpleTextAttributes): SeBasicItemPresentationBuilder {
    this.textAttributes = attributes
    return this
  }

  fun withSelectedTextAttributes(attributes: SimpleTextAttributes?): SeBasicItemPresentationBuilder {
    this.selectedTextAttributes = attributes
    return this
  }

  fun withDescription(description: @NlsSafe String?): SeBasicItemPresentationBuilder {
    this.description = description
    return this
  }

  fun withAccessibleAdditionToText(accessibleAdditionToText: @NlsSafe String?): SeBasicItemPresentationBuilder {
    this.accessibleAdditionToText = accessibleAdditionToText
    return this
  }

  fun withExtendedInfo(extendedInfo: SeExtendedInfo?): SeBasicItemPresentationBuilder {
    this.extendedInfo = extendedInfo
    return this
  }

  fun withMultiSelectionSupported(isMultiSelectionSupported: Boolean): SeBasicItemPresentationBuilder {
    this.isMultiSelectionSupported = isMultiSelectionSupported
    return this
  }

  fun build(): SeBasicItemPresentation =
    SeBasicItemPresentationImpl(
      iconId = iconId,
      textChunk = serializableTextChunk(text, textAttributes),
      selectedTextChunk = selectedTextAttributes?.let { serializableTextChunk(text, it) },
      description = description,
      accessibleAdditionToText = accessibleAdditionToText,
      extendedInfo = extendedInfo,
      isMultiSelectionSupported = isMultiSelectionSupported
    )

  private fun serializableTextChunk(text: String?, attributes: SimpleTextAttributes?) =
    text?.let { text ->
      attributes?.let { attributes ->
        SerializableTextChunk(text, attributes)
      } ?: SerializableTextChunk(text)
    } ?: SerializableTextChunk("")
}

@ApiStatus.Internal
@Serializable
class SeBasicItemPresentationImpl internal constructor(
  val iconId: IconId?,
  val textChunk: SerializableTextChunk,
  val selectedTextChunk: SerializableTextChunk?,
  val description: @NlsSafe String?,
  val accessibleAdditionToText: @NlsSafe String?,
  override val extendedInfo: SeExtendedInfo?,
  override val isMultiSelectionSupported: Boolean,
) : SeBasicItemPresentation {
  override val text: @Nls String get() = textChunk.text

  @ApiStatus.Internal
  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeBasicItemPresentationImpl) return false

    return super.contentEquals(other) &&
           selectedTextChunk?.text == other.selectedTextChunk?.text &&
           description == other.description &&
           accessibleAdditionToText == other.accessibleAdditionToText
  }
}