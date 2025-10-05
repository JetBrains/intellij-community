// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.lang.Language
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.evaluation.EvaluationMode
import fleet.rpc.core.RpcFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XExpressionDto(
  val expression: String,
  val language: LanguageDto?,
  val customInfo: String?,
  val mode: EvaluationMode,
)

@ApiStatus.Internal
fun XExpression.toRpc(): XExpressionDto {
  return XExpressionDto(expression, language?.toRpc(), customInfo, mode)
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
    return dto.language?.language()
  }

  override fun getCustomInfo(): String? {
    return dto.customInfo
  }

  override fun getMode(): EvaluationMode {
    return dto.mode
  }
}

@ApiStatus.Internal
@Serializable
data class LanguageDto(
  val id: String,
  @Transient val language: Language? = null,
)

private fun Language.toRpc(): LanguageDto = LanguageDto(id, this)

private fun LanguageDto.language(): Language? = language ?: Language.findLanguageByID(id)

@ApiStatus.Internal
@Serializable
data class XExpressionDocumentDto(
  val backendDocumentId: BackendDocumentId,
  val expressionFlow: RpcFlow<XExpressionDto>,
)
