// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.*
import com.intellij.webSymbols.query.impl.WebSymbolMatchImpl

interface WebSymbolMatch : CompositeWebSymbol {

  val matchedName: @NlsSafe String

  companion object {


    @JvmStatic
    @JvmOverloads
    fun create(matchedName: String,
               nameSegments: List<WebSymbolNameSegment>,
               namespace: SymbolNamespace,
               kind: SymbolKind,
               origin: WebSymbolOrigin,
               explicitPriority: WebSymbol.Priority? = null,
               explicitProximity: Int? = null): WebSymbolMatch =
      WebSymbolMatchImpl.create(matchedName, nameSegments, namespace, kind, origin, explicitPriority, explicitProximity)
  }

}