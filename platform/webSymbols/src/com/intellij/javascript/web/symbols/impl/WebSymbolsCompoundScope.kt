package com.intellij.javascript.web.symbols.impl

import com.intellij.javascript.web.symbols.*
import com.intellij.model.Pointer

class WebSymbolsCompoundScope(private val scopes: List<WebSymbolsScope>) : WebSymbolsScope {

  override fun apply(matches: List<WebSymbol>, strict: Boolean,
                     namespace: WebSymbolsContainer.Namespace?,
                     kind: SymbolKind,
                     name: String?): List<WebSymbol> =
    scopes.foldRight(matches) { scope, list ->
      scope.apply(list, strict, namespace, kind, name)
    }

  override fun apply(item: WebSymbolCodeCompletionItem,
                     namespace: WebSymbolsContainer.Namespace?,
                     kind: SymbolKind): WebSymbolCodeCompletionItem? =
    scopes.foldRight(item as WebSymbolCodeCompletionItem?) { scope, i ->
      i?.let { scope.apply(it, namespace, kind) }
    }

  override fun createPointer(): Pointer<out WebSymbolsScope> {
    val scopePointers = scopes.map { it.createPointer() }
    return Pointer {
      val scopes = scopePointers.map { it.dereference() }
      if (scopes.any { it == null }) return@Pointer null
      @Suppress("UNCHECKED_CAST")
      WebSymbolsCompoundScope(scopes as List<WebSymbolsScope>)
    }
  }

  override fun getModificationCount(): Long =
    scopes.sumOf { it.modificationCount }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is WebSymbolsCompoundScope
    && other.scopes == scopes

  override fun hashCode(): Int =
    scopes.hashCode()

}