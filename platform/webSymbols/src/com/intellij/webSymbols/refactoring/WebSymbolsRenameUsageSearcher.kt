// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.registry.WebSymbolsRegistry
import com.intellij.webSymbols.registry.WebSymbolsRegistryManager
import com.intellij.webSymbols.search.WebSymbolsUsageSearcher

class WebSymbolsRenameUsageSearcher : RenameUsageSearcher {

  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<@JvmWildcard Query<out RenameUsage>> =
    parameters.target
      .let { it as? WebSymbol ?: (it as? WebSymbolRenameTarget)?.symbol }
      ?.let { symbol ->
        WebSymbolsUsageSearcher.buildWebSymbolUsagesQueries(symbol, parameters.project, parameters.searchScope)
          .map { query ->
            query.mapping {
              WebSymbolPsiModifiableRenameUsage(WebSymbolsRegistryManager.get(it.file), symbol, PsiRenameUsage.defaultPsiRenameUsage(it))
            }
          }
      }
    ?: emptyList()

  private class WebSymbolPsiModifiableRenameUsage(private val registry: WebSymbolsRegistry,
                                                  private val symbol: WebSymbol,
                                                  private val psiRenameUsage: PsiRenameUsage)
    : PsiRenameUsage by psiRenameUsage, PsiModifiableRenameUsage {

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
      get() = fileRangeUpdater {
        symbol.adjustNameForRefactoring(registry, it, range.substring(file.text))
      }

    override fun createPointer(): Pointer<out PsiModifiableRenameUsage> {
      val registryPtr = registry.createPointer()
      val symbolPtr = symbol.createPointer()
      val usagePtr = psiRenameUsage.createPointer()
      return Pointer {
        val registry = registryPtr.dereference() ?: return@Pointer null
        val symbol = symbolPtr.dereference() ?: return@Pointer null
        val usage = usagePtr.dereference() ?: return@Pointer null
        WebSymbolPsiModifiableRenameUsage(registry, symbol, usage)
      }
    }
  }

}