package com.intellij.webSymbols.impl

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StringDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.util.IconUtil
import com.intellij.util.containers.Interner
import com.intellij.util.ui.JBUI
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryParams
import com.intellij.webSymbols.webTypes.json.WebTypes
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon


internal val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .setTypeFactory(TypeFactory.defaultInstance().withClassLoader(WebTypes::class.java.classLoader))
  .registerModule(SimpleModule().also { module ->
    val interner = Interner.createStringInterner()
    module.addDeserializer(String::class.java, object : StringDeserializer() {
      override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
        return super.deserialize(p, ctxt)?.let { interner.intern(it) }
      }
    })
  })

internal fun Icon.scaleToHeight(height: Int): Icon {
  val scale = JBUI.scale(height).toFloat() / this.iconHeight.toFloat()
  return IconUtil.scale(this, null, scale)
}

internal fun <T> List<T>.selectBest(
  segmentsProvider: (T) -> List<WebSymbolNameSegment>,
  priorityProvider: (T) -> WebSymbol.Priority?,
  isExtension: (T) -> Boolean,
) =
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
    symbol.matchContext(params.queryExecutor.context)
    && ((params as? WebSymbolsNameMatchQueryParams)?.abstractSymbols == true
        || (params as? WebSymbolsListSymbolsQueryParams)?.abstractSymbols == true
        || !symbol.abstract)
    && (params.virtualSymbols != false || !symbol.virtual)
  }

internal fun WebSymbolNameSegment.withOffset(offset: Int): WebSymbolNameSegmentImpl =
  (this as WebSymbolNameSegmentImpl).withOffset(offset)

internal fun WebSymbolNameSegment.withDisplayName(displayName: String?) =
  (this as WebSymbolNameSegmentImpl).withDisplayName(displayName)

internal fun WebSymbolNameSegment.withRange(start: Int, end: Int) =
  (this as WebSymbolNameSegmentImpl).withRange(start, end)

internal val WebSymbolNameSegment.highlightingEnd: Int?
  get() =
    (this as WebSymbolNameSegmentImpl).highlightingEnd

internal fun WebSymbolNameSegment.copy(
  apiStatus: WebSymbolApiStatus? = null,
  priority: WebSymbol.Priority? = null,
  proximity: Int? = null,
  problem: WebSymbolNameSegment.MatchProblem? = null,
  symbols: List<WebSymbol> = emptyList(),
  highlightEnd: Int? = null,
): WebSymbolNameSegmentImpl =
  (this as WebSymbolNameSegmentImpl).copy(apiStatus, priority, proximity, problem, symbols, highlightEnd)

@ApiStatus.Internal
fun WebSymbolNameSegment.canUnwrapSymbols(): Boolean =
  (this as WebSymbolNameSegmentImpl).canUnwrapSymbols()

internal fun WebSymbol.removeZeroLengthSegmentsRecursively(): List<WebSymbol> {
  if (this !is WebSymbolMatch) return listOf(this)
  val nameLength = matchedName.length
  return nameSegments
           .takeIf { it.size > 1 && it.none { segment -> segment.problem != null } }
           ?.find { segment -> segment.start == 0 && segment.end == nameLength }
           ?.let { segment -> segment.symbols.flatMap { it.removeZeroLengthSegmentsRecursively() } }
         ?: listOf(this)
}