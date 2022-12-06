package com.intellij.webSymbols.impl

import com.intellij.util.IconUtil
import com.intellij.util.containers.Stack
import com.intellij.util.ui.JBUI
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.impl.applyIcons
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryParams
import javax.swing.Icon

internal fun Icon.scaleToHeight(height: Int): Icon {
  val scale = JBUI.scale(height).toFloat() / this.iconHeight.toFloat()
  return IconUtil.scale(this, null, scale)
}

internal fun <T> List<T>.selectBest(segmentsProvider: (T) -> List<WebSymbolNameSegment>,
                                    priorityProvider: (T) -> WebSymbol.Priority?,
                                    isExtension: (T) -> Boolean) =
  if (size > 1) {
    var bestWeight: IntArray = intArrayOf(0, 0, 0)

    val results = mutableListOf<Pair<T, IntArray>>()
    val extensions = mutableListOf<T>()
    forEach { item ->
      if (!isExtension(item)) {
        val weight: IntArray = intArrayOf(
          // match length without a problem
          segmentsProvider(item).find { it.problem != null }?.start ?: Int.MAX_VALUE,
          // priority
          (priorityProvider(item) ?: WebSymbol.Priority.NORMAL).ordinal,
          //  match length of static part of RegExp
          segmentsProvider(item).sumOf { it.matchScore }
        )
        results.add(Pair(item, weight))
        for (i in 0..2) {
          if (bestWeight[i] < weight[i]) {
            bestWeight = weight
            break
          }
        }
      }
      else {
        extensions.add(item)
      }
    }
    results
      .mapNotNull { (item, weight) ->
        if (bestWeight.contentEquals(weight)) {
          item
        }
        else null
      }
      .plus(extensions)
  }
  else this

internal fun List<WebSymbol>.sortSymbolsByPriority(extensionsLast: Boolean = true): List<WebSymbol> =
  sortedWith(Comparator.comparingInt<WebSymbol> { if (it.extension && extensionsLast) 1 else 0 }
               .thenComparingInt { -(it.priority ?: WebSymbol.Priority.NORMAL).ordinal }
               .thenComparingInt { -(it.proximity ?: 0) })

internal fun <T : WebSymbol> Sequence<T>.filterByQueryParams(params: WebSymbolsQueryParams): Sequence<T> =
  this.filter { symbol ->
    symbol.origin.framework.let { it == null || it == params.framework }
    && ((params as? WebSymbolsNameMatchQueryParams)?.abstractSymbols == true || !symbol.abstract)
    && ((params as? WebSymbolsNameMatchQueryParams)?.virtualSymbols != false || !symbol.virtual)
  }

internal fun WebSymbol.toCodeCompletionItems(name: String?,
                                             params: WebSymbolsCodeCompletionQueryParams,
                                             context: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
  pattern?.let { pattern ->
    context.push(this)
    try {
      pattern.getCompletionResults(this, context, name ?: "", params)
        .applyIcons(this)
    }
    finally {
      context.pop()
    }
  }
  ?: params.queryExecutor.namesProvider
    .getNames(namespace, kind, this.name, WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
    .map { WebSymbolCodeCompletionItem.create(it, 0, symbol = this) }
