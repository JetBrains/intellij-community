// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import com.intellij.model.Pointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.patterns.PolySymbolsPatternFactory
import com.intellij.webSymbols.patterns.WebSymbolsPatternReferenceResolver
import java.util.Objects

/**
 * A utility [PolySymbol], which allows to reference
 * symbols from other namespace or kind.
 */
class ReferencingPolySymbol private constructor(
  override val namespace: SymbolNamespace,
  override val kind: SymbolKind,
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
        qualifiedKind.namespace, qualifiedKind.kind, name,
        origin, *qualifiedKinds, priority = priority, location = location
      )
  }

  private val references = references.toList()

  override val pattern: PolySymbolsPattern =
    PolySymbolsPatternFactory.createComplexPattern(
      ComplexPatternOptions(
        priority = priority,
        symbolsResolver = WebSymbolsPatternReferenceResolver(
          *references.map {
            WebSymbolsPatternReferenceResolver.Reference(qualifiedKind = it, location = location)
          }.toTypedArray()
        )), false,
      PolySymbolsPatternFactory.createPatternSequence(
        PolySymbolsPatternFactory.createSymbolReferencePlaceholder(name),
      )
    )

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is ReferencingPolySymbol
    && other.namespace == namespace
    && other.kind == kind
    && other.name == name
    && other.origin == origin
    && other.priority == priority
    && other.location == location
    && other.references == references

  override fun hashCode(): Int =
    Objects.hash(namespace, kind, name, origin, priority, location, references)

  override fun createPointer(): Pointer<out PolySymbol> =
    Pointer.hardPointer(this)

}