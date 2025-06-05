// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.*
import com.intellij.polySymbols.query.impl.PolySymbolMatchBase
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
      nameSegments: List<PolySymbolNameSegment>,
      qualifiedKind: PolySymbolQualifiedKind,
      origin: PolySymbolOrigin,
      explicitPriority: PolySymbol.Priority? = null,
    ): PolySymbolMatch =
      PolySymbolMatchBase.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { builder ->
          builder.addNameSegments(nameSegments)
          explicitPriority?.let { builder.explicitPriority(it) }
        }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: PolySymbolQualifiedKind,
      origin: PolySymbolOrigin,
      builder: (PolySymbolMatchBuilder.() -> Unit),
    ): PolySymbolMatch =
      PolySymbolMatchBase.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { builder.invoke(it) }
        .build()

    @JvmStatic
    fun create(
      matchedName: String,
      qualifiedKind: PolySymbolQualifiedKind,
      origin: PolySymbolOrigin,
      vararg nameSegments: PolySymbolNameSegment,
    ): PolySymbolMatch =
      PolySymbolMatchBase.BuilderImpl(matchedName, qualifiedKind, origin)
        .also { it.addNameSegments(*nameSegments) }
        .build()

  }

}