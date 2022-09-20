// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker

interface WebSymbolNamesProvider : ModificationTracker {

  fun createPointer(): Pointer<WebSymbolNamesProvider>

  fun getNames(namespace: WebSymbolsContainer.Namespace,
               kind: SymbolKind,
               name: String,
               target: Target): List<String>

  fun adjustRename(namespace: WebSymbolsContainer.Namespace,
                   kind: SymbolKind,
                   oldName: String,
                   newName: String,
                   occurence: String): String

  fun withRules(rules: List<WebSymbolNameConversionRules>): WebSymbolNamesProvider

  enum class Target {
    CODE_COMPLETION_VARIANTS,
    NAMES_MAP_STORAGE,
    NAMES_QUERY
  }

}