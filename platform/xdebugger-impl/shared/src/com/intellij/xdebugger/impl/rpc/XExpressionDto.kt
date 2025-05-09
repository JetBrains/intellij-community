// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.lang.Language
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.evaluation.EvaluationMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

// TODO: move to RPC module!!
@ApiStatus.Internal
@Serializable
data class XExpressionDto(
  val expression: String,
  // TODO[IJPL-160146]: Serialize Language
  @Transient val language: Language? = null,
  val customInfo: String?,
  val mode: EvaluationMode,
)

@ApiStatus.Internal
fun XExpression.toRpc(): XExpressionDto {
  return XExpressionDto(expression, language, customInfo, mode)
}

@ApiStatus.Internal
fun XExpressionDto.xExpression(): XExpression {
  return SerializedXExpression(this)
}

private class SerializedXExpression(private val dto: XExpressionDto) : XExpression {
  override fun getExpression(): String {
    return dto.expression
  }

  override fun getLanguage(): Language? {
    return dto.language
  }

  override fun getCustomInfo(): String? {
    return dto.customInfo
  }

  override fun getMode(): EvaluationMode {
    return dto.mode
  }
}