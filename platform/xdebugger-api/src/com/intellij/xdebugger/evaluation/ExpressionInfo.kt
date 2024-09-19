// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.evaluation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
class ExpressionInfo @JvmOverloads constructor(
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
  // ignore [PsiElement] in serialization for now, since it is not serializable
  // TODO: [ExpressionInfo] shouldn't contain PsiElement
  @Transient
  val element: PsiElement? = null,
)

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