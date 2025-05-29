package com.intellij.polySymbols.impl

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
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.polySymbols.query.WebSymbolsQueryParams
import com.intellij.polySymbols.webTypes.json.WebTypes
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
  segmentsProvider: (T) -> List<PolySymbolNameSegment>,
  priorityProvider: (T) -> PolySymbol.Priority?,
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
          (priorityProvider(item) ?: PolySymbol.Priority.NORMAL).ordinal,
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

internal fun List<PolySymbol>.sortSymbolsByPriority(extensionsLast: Boolean = true): List<PolySymbol> =
  sortedWith(Comparator.comparingInt<PolySymbol> { if (it.extension && extensionsLast) 1 else 0 }
               .thenComparingInt { -(it.priority ?: PolySymbol.Priority.NORMAL).ordinal }
               .thenComparingInt { -(it.proximity ?: 0) })

internal fun <T : PolySymbol> Sequence<T>.filterByQueryParams(params: WebSymbolsQueryParams): Sequence<T> =
  this.filter { symbol ->
    symbol.matchContext(params.queryExecutor.context)
    && ((params as? WebSymbolsNameMatchQueryParams)?.abstractSymbols == true
        || (params as? WebSymbolsListSymbolsQueryParams)?.abstractSymbols == true
        || !symbol.abstract)
    && (params.virtualSymbols != false || !symbol.virtual)
  }

internal fun PolySymbolNameSegment.withOffset(offset: Int): PolySymbolNameSegmentImpl =
  (this as PolySymbolNameSegmentImpl).withOffset(offset)

internal fun PolySymbolNameSegment.withDisplayName(displayName: String?) =
  (this as PolySymbolNameSegmentImpl).withDisplayName(displayName)

internal fun PolySymbolNameSegment.withRange(start: Int, end: Int) =
  (this as PolySymbolNameSegmentImpl).withRange(start, end)

internal val PolySymbolNameSegment.highlightingEnd: Int?
  get() =
    (this as PolySymbolNameSegmentImpl).highlightingEnd

internal fun PolySymbolNameSegment.copy(
  apiStatus: PolySymbolApiStatus? = null,
  priority: PolySymbol.Priority? = null,
  proximity: Int? = null,
  problem: PolySymbolNameSegment.MatchProblem? = null,
  symbols: List<PolySymbol> = emptyList(),
  highlightEnd: Int? = null,
): PolySymbolNameSegmentImpl =
  (this as PolySymbolNameSegmentImpl).copy(apiStatus, priority, proximity, problem, symbols, highlightEnd)

@ApiStatus.Internal
fun PolySymbolNameSegment.canUnwrapSymbols(): Boolean =
  (this as PolySymbolNameSegmentImpl).canUnwrapSymbols()

internal fun PolySymbol.removeZeroLengthSegmentsRecursively(): List<PolySymbol> {
  if (this !is PolySymbolMatch) return listOf(this)
  val nameLength = matchedName.length
  return nameSegments
           .takeIf { it.size > 1 && it.none { segment -> segment.problem != null } }
           ?.find { segment -> segment.start == 0 && segment.end == nameLength }
           ?.let { segment -> segment.symbols.flatMap { it.removeZeroLengthSegmentsRecursively() } }
         ?: listOf(this)
}