// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.util.NlsActions
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents additional information associated with search results or items in Search Everywhere.
 * It's supposed to be rendered at the bottom panel of the Search Everywhere popup
 *
 * See [SeExtendedInfoBuilder]
 */
@ApiStatus.Experimental
@Serializable
sealed interface SeExtendedInfo {
  val text: String?
  @get:NlsActions.ActionText
  val actionText: String?
  @get:NlsActions.ActionDescription
  val actionDescription: String?
  val keyCode: Int?
  val modifiers: Int?
}

/**
 * Builder for [SeExtendedInfo]
 */
@ApiStatus.Experimental
class SeExtendedInfoBuilder {
  var text: String? = null
  var actionText: String? = null
  var actionDescription: String? = null
  var keyCode: Int? = null
  var modifiers: Int? = null

  fun withExtendedInfo(extendedInfo: ExtendedInfo?, item: Any): SeExtendedInfoBuilder {
    val rightAction = extendedInfo?.rightAction?.invoke(item)
    val keyStroke = rightAction?.shortcutSet?.shortcuts
      ?.filterIsInstance<KeyboardShortcut>()
      ?.firstOrNull()
      ?.firstKeyStroke

    return withText(extendedInfo?.leftText?.invoke(item))
      .withActionText(rightAction?.templatePresentation?.text)
      .withActionDescription(rightAction?.templatePresentation?.description)
      .withKeyCode(keyStroke?.keyCode)
      .withModifiers(keyStroke?.modifiers)
  }

  fun withText(text: String?): SeExtendedInfoBuilder {
    this.text = text
    return this
  }

  fun withActionText(actionText: String?): SeExtendedInfoBuilder {
    this.actionText = actionText
    return this
  }

  fun withActionDescription(actionDescription: String?): SeExtendedInfoBuilder {
    this.actionDescription = actionDescription
    return this
  }

  fun withKeyCode(keyCode: Int?): SeExtendedInfoBuilder {
    this.keyCode = keyCode
    return this
  }

  fun withModifiers(modifiers: Int?): SeExtendedInfoBuilder {
    this.modifiers = modifiers
    return this
  }

  fun build(): SeExtendedInfo = SeExtendedInfoImpl(text, actionText, actionDescription, keyCode, modifiers)
}

@ApiStatus.Internal
@Serializable
private class SeExtendedInfoImpl(
  override val text: String?,
  @NlsActions.ActionText override val actionText: String?,
  @NlsActions.ActionDescription override val actionDescription: String?,
  override val keyCode: Int?,
  override val modifiers: Int?,
) : SeExtendedInfo{

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SeExtendedInfoImpl) return false

    return text == other.text &&
           actionText == other.actionText &&
           actionDescription == other.actionDescription &&
           keyCode == other.keyCode &&
           modifiers == other.modifiers
  }

  override fun hashCode(): Int {
    var result = text?.hashCode() ?: 0
    result = 31 * result + (actionText?.hashCode() ?: 0)
    result = 31 * result + (actionDescription?.hashCode() ?: 0)
    result = 31 * result + (keyCode ?: 0)
    result = 31 * result + (modifiers ?: 0)
    return result
  }
}