// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.patterns.PolySymbolsPatternFactory

/**
 * A utility [PolySymbol], which allows to map
 * from one symbol to another symbol.
 *
 * The mapping will be made, if the [MappedPolySymbol.name]
 * matches exactly the queried name. As a result,
 * a reference to the symbol resolved through the
 * `mappingPath` will be created.
 */
class MappedPolySymbol private constructor(
  override val qualifiedKind: PolySymbolQualifiedKind,
  override val name: String,
  override val origin: PolySymbolOrigin,
  vararg mappingPath: PolySymbolQualifiedName,
  override val priority: PolySymbol.Priority? = null,
) : PolySymbol {

  companion object {
    @JvmOverloads
    @JvmStatic
    fun create(
      qualifiedKind: PolySymbolQualifiedKind,
      name: String,
      origin: PolySymbolOrigin,
      vararg mappingPath: PolySymbolQualifiedName,
      priority: PolySymbol.Priority? = null,
    ): MappedPolySymbol =
      MappedPolySymbol(qualifiedKind, name, origin, *mappingPath, priority = priority)
  }

  override val pattern: PolySymbolsPattern =
    PolySymbolsPatternFactory.createSingleSymbolReferencePattern(mappingPath.toList())

  override fun createPointer(): Pointer<out PolySymbol> =
    Pointer.hardPointer(this)
}