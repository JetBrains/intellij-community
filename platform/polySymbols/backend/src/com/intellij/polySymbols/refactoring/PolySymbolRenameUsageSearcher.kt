// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.search.PolySymbolUsageQueries
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query

internal class PolySymbolRenameUsageSearcher : RenameUsageSearcher {

  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<@JvmWildcard Query<out RenameUsage>> =
    parameters.target
      .let { it as? PolySymbol ?: (it as? PolySymbolRenameTarget)?.symbol }
      ?.let { symbol ->
        PolySymbolUsageQueries.buildPolySymbolUsagesQueries(symbol, parameters.project, parameters.searchScope)
          .map { query ->
            query.mapping {
              PolySymbolPsiModifiableRenameUsage(
                PolySymbolQueryExecutorFactory.create(PsiTreeUtil.findElementOfClassAtRange(it.file, it.range.startOffset, it.range.endOffset, PsiElement::class.java)
                                                      ?: it.file), symbol,
                PsiRenameUsage.defaultPsiRenameUsage(it))
            }
          }
      }
    ?: emptyList()

  private class PolySymbolPsiModifiableRenameUsage(
    private val queryExecutor: PolySymbolQueryExecutor,
    private val symbol: PolySymbol,
    private val psiRenameUsage: PsiRenameUsage,
  ) : PsiRenameUsage by psiRenameUsage, PsiModifiableRenameUsage {

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
      get() = fileRangeUpdater {
        symbol.adjustNameForRefactoring(queryExecutor, it, range.substring(file.text))
      }

    override fun createPointer(): Pointer<out PsiModifiableRenameUsage> {
      val queryExecutorPtr = queryExecutor.createPointer()
      val symbolPtr = symbol.createPointer()
      val usagePtr = psiRenameUsage.createPointer()
      return Pointer {
        val queryExecutor = queryExecutorPtr.dereference() ?: return@Pointer null
        val symbol = symbolPtr.dereference() ?: return@Pointer null
        val usage = usagePtr.dereference() ?: return@Pointer null
        PolySymbolPsiModifiableRenameUsage(queryExecutor, symbol, usage)
      }
    }
  }

}