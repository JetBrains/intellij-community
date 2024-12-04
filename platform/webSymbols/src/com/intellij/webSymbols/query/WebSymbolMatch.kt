// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.*
import com.intellij.webSymbols.query.impl.WebSymbolMatchImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface WebSymbolMatch : CompositeWebSymbol {

  val matchedName: @NlsSafe String

  fun withCustomProperties(properties: Map<String, Any>): WebSymbolMatch

  companion object {

    @JvmStatic
    @JvmOverloads
    @ApiStatus.Internal
    fun create(
      matchedName: String,
      nameSegments: List<WebSymbolNameSegment>,
      namespace: SymbolNamespace,
      kind: SymbolKind,
      origin: WebSymbolOrigin,
      explicitPriority: WebSymbol.Priority? = null,
      explicitProximity: Int? = null,
    ): WebSymbolMatch =
      WebSymbolMatchImpl.BuilderImpl(matchedName, WebSymbolQualifiedKind(namespace, kind), origin)
        .also { builder ->
          builder.addNameSegments(nameSegments)
          explicitProximity?.let { builder.explicitProximity(it) }
          explicitPriority?.let { builder.explicitPriority(it) }
        }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: WebSymbolQualifiedKind,
      origin: WebSymbolOrigin,
      builder: (WebSymbolMatchBuilder.() -> Unit),
    ): WebSymbolMatch =
      WebSymbolMatchImpl.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { builder.invoke(it) }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: WebSymbolQualifiedKind,
      origin: WebSymbolOrigin,
      vararg nameSegments: WebSymbolNameSegment,
    ): WebSymbolMatch =
      WebSymbolMatchImpl.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { it.addNameSegments(*nameSegments) }
        .build()

  }

}