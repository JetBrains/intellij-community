// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.*
import com.intellij.webSymbols.query.impl.PolySymbolMatchImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolMatch : CompositePolySymbol {

  val matchedName: @NlsSafe String

  fun withCustomProperties(properties: Map<String, Any>): PolySymbolMatch

  companion object {

    @JvmStatic
    @JvmOverloads
    @ApiStatus.Internal
    fun create(
      matchedName: String,
      nameSegments: List<WebSymbolNameSegment>,
      namespace: SymbolNamespace,
      kind: SymbolKind,
      origin: PolySymbolOrigin,
      explicitPriority: PolySymbol.Priority? = null,
      explicitProximity: Int? = null,
    ): PolySymbolMatch =
      PolySymbolMatchImpl.BuilderImpl(matchedName, PolySymbolQualifiedKind(namespace, kind), origin)
        .also { builder ->
          builder.addNameSegments(nameSegments)
          explicitProximity?.let { builder.explicitProximity(it) }
          explicitPriority?.let { builder.explicitPriority(it) }
        }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: PolySymbolQualifiedKind,
      origin: PolySymbolOrigin,
      builder: (WebSymbolMatchBuilder.() -> Unit),
    ): PolySymbolMatch =
      PolySymbolMatchImpl.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { builder.invoke(it) }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: PolySymbolQualifiedKind,
      origin: PolySymbolOrigin,
      vararg nameSegments: WebSymbolNameSegment,
    ): PolySymbolMatch =
      PolySymbolMatchImpl.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { it.addNameSegments(*nameSegments) }
        .build()

  }

}