package com.intellij.javascript.web.symbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import java.util.function.Function

interface WebSymbolNameConversionRules: ModificationTracker {

  val canonicalNamesProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>
  val matchNamesProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>
  val nameVariantsProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>

  fun createPointer(): Pointer<out WebSymbolNameConversionRules>

}