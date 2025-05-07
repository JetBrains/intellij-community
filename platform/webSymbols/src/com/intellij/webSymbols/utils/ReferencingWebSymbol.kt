// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import com.intellij.model.Pointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternFactory
import com.intellij.webSymbols.patterns.WebSymbolsPatternReferenceResolver
import java.util.Objects

/**
 * A utility [WebSymbol], which allows to reference
 * symbols from other namespace or kind.
 */
class ReferencingWebSymbol private constructor(
  override val namespace: SymbolNamespace,
  override val kind: SymbolKind,
  override val name: String,
  override val origin: WebSymbolOrigin,
  vararg references: WebSymbolQualifiedKind,
  override val priority: WebSymbol.Priority?,
  private val location: List<WebSymbolQualifiedName> = emptyList(),
) : WebSymbol {

  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(
      qualifiedKind: WebSymbolQualifiedKind,
      name: String,
      origin: WebSymbolOrigin,
      vararg qualifiedKinds: WebSymbolQualifiedKind,
      priority: WebSymbol.Priority? = null,
      location: List<WebSymbolQualifiedName> = emptyList(),
    ): ReferencingWebSymbol =
      ReferencingWebSymbol(
        qualifiedKind.namespace, qualifiedKind.kind, name,
        origin, *qualifiedKinds, priority = priority, location = location
      )
  }

  private val references = references.toList()

  override val pattern: WebSymbolsPattern =
    WebSymbolsPatternFactory.createComplexPattern(
      ComplexPatternOptions(
        priority = priority,
        symbolsResolver = WebSymbolsPatternReferenceResolver(
          *references.map {
            WebSymbolsPatternReferenceResolver.Reference(qualifiedKind = it, location = location)
          }.toTypedArray()
        )), false,
      WebSymbolsPatternFactory.createPatternSequence(
        WebSymbolsPatternFactory.createSymbolReferencePlaceholder(name),
      )
    )

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is ReferencingWebSymbol
    && other.namespace == namespace
    && other.kind == kind
    && other.name == name
    && other.origin == origin
    && other.priority == priority
    && other.location == location
    && other.references == references

  override fun hashCode(): Int =
    Objects.hash(namespace, kind, name, origin, priority, location, references)

  override fun createPointer(): Pointer<out WebSymbol> =
    Pointer.hardPointer(this)

}