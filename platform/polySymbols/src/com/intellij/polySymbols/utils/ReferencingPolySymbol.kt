// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.ComplexPatternOptions
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.patterns.PolySymbolsPatternFactory
import com.intellij.polySymbols.patterns.PolySymbolsPatternReferenceResolver

/**
 * A utility [PolySymbol], which allows to reference
 * symbols from other namespace or kind.
 */
class ReferencingPolySymbol private constructor(
  override val qualifiedKind: PolySymbolQualifiedKind,
  override val name: String,
  override val origin: PolySymbolOrigin,
  vararg references: PolySymbolQualifiedKind,
  override val priority: PolySymbol.Priority?,
  private val location: List<PolySymbolQualifiedName> = emptyList(),
) : PolySymbol {

  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(
      qualifiedKind: PolySymbolQualifiedKind,
      name: String,
      origin: PolySymbolOrigin,
      vararg qualifiedKinds: PolySymbolQualifiedKind,
      priority: PolySymbol.Priority? = null,
      location: List<PolySymbolQualifiedName> = emptyList(),
    ): ReferencingPolySymbol =
      ReferencingPolySymbol(
        qualifiedKind, name, origin, *qualifiedKinds, priority = priority, location = location
      )
  }

  private val references = references.toList()

  override val pattern: PolySymbolsPattern =
    PolySymbolsPatternFactory.createComplexPattern(
      ComplexPatternOptions(
        priority = priority,
        symbolsResolver = PolySymbolsPatternReferenceResolver(
          *references.map {
            PolySymbolsPatternReferenceResolver.Reference(qualifiedKind = it, location = location)
          }.toTypedArray()
        )), false,
      PolySymbolsPatternFactory.createPatternSequence(
        PolySymbolsPatternFactory.createSymbolReferencePlaceholder(name),
      )
    )

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is ReferencingPolySymbol
    && other.qualifiedKind == qualifiedKind
    && other.name == name
    && other.origin == origin
    && other.priority == priority
    && other.location == location
    && other.references == references

  override fun hashCode(): Int {
    var result = qualifiedKind.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + origin.hashCode()
    result = 31 * result + priority.hashCode()
    result = 31 * result + location.hashCode()
    result = 31 * result + references.hashCode()
    return result
  }

  override fun createPointer(): Pointer<out PolySymbol> =
    Pointer.hardPointer(this)

}