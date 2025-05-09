// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.editor.colors.TextAttributesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface XValueSerializedPresentation {
  @ApiStatus.Internal
  @Serializable
  data class SimplePresentation(
    @JvmField val icon: IconId?,
    @JvmField val presentationType: String?,
    @JvmField val value: String,
    @JvmField val hasChildren: Boolean,
  ) : XValueSerializedPresentation

  @ApiStatus.Internal
  @Serializable
  data class AdvancedPresentation(
    @JvmField val icon: IconId?,
    @JvmField val hasChildren: Boolean,
    @JvmField val separator: String,
    @JvmField val isShownName: Boolean,
    @JvmField val presentationType: String?,
    @JvmField val isAsync: Boolean,
    @JvmField val parts: List<XValueAdvancedPresentationPart>,
  ) : XValueSerializedPresentation
}

@ApiStatus.Internal
@Serializable
sealed interface XValueAdvancedPresentationPart {
  @ApiStatus.Internal
  @Serializable
  data class Value(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class StringValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class NumericValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class KeywordValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  // TODO[IJPL-160146]: support [TextAttributesKey] serialization
  @ApiStatus.Internal
  @Serializable
  data class ValueWithAttributes(@JvmField val value: String, @Transient @JvmField val key: TextAttributesKey? = null) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class StringValueWithHighlighting(
    @JvmField val value: String,
    @JvmField val additionalSpecialCharsToHighlight: String?,
    @JvmField val maxLength: Int,
  ) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class Comment(@JvmField val comment: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class SpecialSymbol(@JvmField val symbol: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class Error(@JvmField val error: String) : XValueAdvancedPresentationPart
}