// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import com.intellij.model.Pointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternFactory

/**
 * A utility [WebSymbol], which allows to map
 * from one symbol to another symbol.
 *
 * The mapping will be made, if the [MappedWebSymbol.name]
 * matches exactly the queried name. As a result,
 * a reference to the symbol resolved through the
 * `mappingPath` will be created.
 */
class MappedWebSymbol private constructor(
  override val namespace: SymbolNamespace,
  override val kind: SymbolKind,
  override val name: String,
  override val origin: WebSymbolOrigin,
  vararg mappingPath: WebSymbolQualifiedName,
  override val priority: WebSymbol.Priority? = null,
) : WebSymbol {

  companion object {
    @JvmOverloads
    @JvmStatic
    fun create(
      qualifiedKind: WebSymbolQualifiedKind,
      name: String,
      origin: WebSymbolOrigin,
      vararg mappingPath: WebSymbolQualifiedName,
      priority: WebSymbol.Priority? = null
    ): MappedWebSymbol =
      MappedWebSymbol(qualifiedKind.namespace, qualifiedKind.kind, name, origin, *mappingPath, priority = priority)
  }

  override val pattern: WebSymbolsPattern =
    WebSymbolsPatternFactory.createSingleSymbolReferencePattern(mappingPath.toList())

  override fun createPointer(): Pointer<out WebSymbol> =
    Pointer.hardPointer(this)
}