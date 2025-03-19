// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation

import com.intellij.openapi.util.TextRange
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@Serializable
class ExpressionInfo @ApiStatus.Internal constructor(
  /**
   * Text range to highlight as link,
   * will be used to compute evaluation and display text if these values not specified.
   */
  @Serializable(with = TextRangeSerializer::class)
  val textRange: TextRange,
  /**
   * Expression to evaluate
   */
  val expressionText: String? = null,
  val displayText: String? = expressionText,
  @ApiStatus.Internal
  val isManualSelection: Boolean,
) {
  @JvmOverloads
  constructor(textRange: TextRange, expressionText: String? = null, displayText: String? = expressionText) : this(
    textRange,
    expressionText,
    displayText,
    isManualSelection = false,
  )
}

// TODO: temporary hack, remove it when [TextRange] will be serializable
private object TextRangeSerializer : KSerializer<TextRange> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TextRange", PrimitiveKind.STRING)
  private val serializer = PairSerializer(Int.serializer(), Int.serializer())

  override fun deserialize(decoder: Decoder): TextRange {
    return serializer.deserialize(decoder).let { TextRange(it.first, it.second) }
  }

  override fun serialize(encoder: Encoder, value: TextRange) {
    serializer.serialize(encoder, Pair(value.startOffset, value.endOffset))
  }
}