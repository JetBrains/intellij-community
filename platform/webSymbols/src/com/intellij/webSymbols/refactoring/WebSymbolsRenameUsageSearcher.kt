// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.search.WebSymbolUsageQueries

internal class WebSymbolsRenameUsageSearcher : RenameUsageSearcher {

  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<@JvmWildcard Query<out RenameUsage>> =
    parameters.target
      .let { it as? WebSymbol ?: (it as? WebSymbolRenameTarget)?.symbol }
      ?.let { symbol ->
        WebSymbolUsageQueries.buildWebSymbolUsagesQueries(symbol, parameters.project, parameters.searchScope)
          .map { query ->
            query.mapping {
              WebSymbolPsiModifiableRenameUsage(
                WebSymbolsQueryExecutorFactory.create(PsiTreeUtil.findElementOfClassAtRange(it.file, it.range.startOffset, it.range.endOffset, PsiElement::class.java)
                                                      ?: it.file), symbol,
                PsiRenameUsage.defaultPsiRenameUsage(it))
            }
          }
      }
    ?: emptyList()

  private class WebSymbolPsiModifiableRenameUsage(
    private val queryExecutor: WebSymbolsQueryExecutor,
    private val symbol: WebSymbol,
    private val psiRenameUsage: PsiRenameUsage,
  )
    : PsiRenameUsage by psiRenameUsage, PsiModifiableRenameUsage {

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
        WebSymbolPsiModifiableRenameUsage(queryExecutor, symbol, usage)
      }
    }
  }

}