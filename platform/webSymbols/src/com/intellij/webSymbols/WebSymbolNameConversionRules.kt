// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import java.util.function.Function

interface WebSymbolNameConversionRules : ModificationTracker {

  val canonicalNamesProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>
  val matchNamesProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>
  val nameVariantsProviders: Map<Triple<FrameworkId?, WebSymbolsContainer.Namespace, SymbolKind>, Function<String, List<String>>>

  fun createPointer(): Pointer<out WebSymbolNameConversionRules>

}