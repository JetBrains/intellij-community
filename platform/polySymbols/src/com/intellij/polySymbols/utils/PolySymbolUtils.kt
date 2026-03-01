// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PolySymbolUtils")

package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.EmptyNavigatable
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.CompositePolySymbol
import com.intellij.polySymbols.PolySymbol.HideFromCompletionProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolKindName
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.impl.PolySymbolNameSegmentImpl
import com.intellij.polySymbols.impl.sortSymbolsByPriority
import com.intellij.polySymbols.impl.withOffset
import com.intellij.polySymbols.impl.withRange
import com.intellij.polySymbols.patterns.impl.applyIcons
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.query.impl.PolySymbolMatchBase
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedList
import javax.swing.Icon
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T : Any, P : Any> T.applyIfNotNull(param: P?, block: T.(P) -> T): T {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return if (param != null)
    block(this, param)
  else this
}

fun List<PolySymbol>.hasOnlyExtensions(): Boolean =
  all { it.extension }

fun List<PolySymbol>.asSingleSymbol(force: Boolean = false): PolySymbol? =
  if (isEmpty())
    null
  else if (size == 1)
    this[0]
  else {
    val first = this[0]
    if (!force && any { it.kind != first.kind })
      null
    else
      PolySymbolMatch.create(first.name, first.kind, PolySymbolNameSegment.create(0, first.name.length, sortSymbolsByPriority()))
  }

fun PolySymbol.withMatchedName(matchedName: String): PolySymbol =
  if (matchedName != name) {
    val nameSegment = if (this is PolySymbolMatch && nameSegments.size == 1)
      nameSegments[0].withRange(0, matchedName.length)
    else
      PolySymbolNameSegment.create(0, matchedName.length, this)
    PolySymbolMatch.create(matchedName, kind, nameSegment)
  }
  else this

fun PolySymbol.withMatchedKind(kind: PolySymbolKind): PolySymbol =
  if (kind != this.kind) {
    val matchedName = this.asSafely<PolySymbolMatch>()?.matchedName ?: name
    val nameSegment = if (this is PolySymbolMatch && nameSegments.size == 1)
      nameSegments[0].withRange(0, matchedName.length)
    else
      PolySymbolNameSegment.create(0, matchedName.length, this)
    PolySymbolMatch.create(matchedName, kind, nameSegment)
  }
  else this

fun PolySymbol.withNavigationTarget(target: PsiElement): PolySymbolDelegate<PolySymbol> =
  PolySymbolWithNavigationTarget(this, target)

private data class PolySymbolWithNavigationTarget(
  override val delegate: PolySymbol,
  private val target: PsiElement,
) : PolySymbolDelegate<PolySymbol> {

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    symbol === this
    || delegate.isEquivalentTo(symbol)
    || (symbol is PolySymbolWithNavigationTarget
        && delegate.isEquivalentTo(symbol.delegate))

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(target))

  override fun createPointer(): Pointer<out PolySymbolDelegate<PolySymbol>> {
    val symbolPtr = delegate.createPointer()
    val targetPtr = target.createSmartPointer()
    return Pointer {
      targetPtr.dereference()?.let { symbolPtr.dereference()?.withNavigationTarget(it) }
    }
  }
}

fun PolySymbol.unwrapMatchedSymbols(): Sequence<PolySymbol> =
  if (this is PolySymbolMatch)
    Sequence {
      object : Iterator<PolySymbol> {
        private var next: PolySymbol? = null
        val fifo = LinkedList<PolySymbol>()

        init {
          fifo.addLast(this@unwrapMatchedSymbols)
          advance()
        }

        private fun advance() {
          while (fifo.isNotEmpty()) {
            val symbol = fifo.removeFirst()
            if (symbol is PolySymbolMatch) {
              symbol.nameSegments.forEach {
                fifo.addAll(it.symbols)
              }
            }
            else {
              next = symbol
              return
            }
          }
          next = null
        }

        override fun hasNext(): Boolean =
          next != null

        override fun next(): PolySymbol =
          next!!.also { advance() }
      }
    }
  else
    sequenceOf(this)

fun PolySymbolNameSegment.withSymbols(symbols: List<PolySymbol>): PolySymbolNameSegment =
  (this as PolySymbolNameSegmentImpl).withSymbols(symbols)

fun PolySymbolMatch.withSegments(segments: List<PolySymbolNameSegment>): PolySymbolMatch =
  (this as PolySymbolMatchBase).withSegments(segments)

fun PolySymbol.match(
  nameToMatch: String,
  params: PolySymbolNameMatchQueryParams,
  stack: PolySymbolQueryStack,
): List<PolySymbol> {
  (this as? PolySymbolWithPattern)?.pattern?.let { pattern ->
    return stack.withSymbols(queryScope) {
      pattern
        .match(this, stack, nameToMatch, params)
        .mapNotNull { matchResult ->
          if ((matchResult.segments.lastOrNull()?.end ?: 0) < nameToMatch.length) {
            null
          }
          else {
            PolySymbolMatch.create(nameToMatch, matchResult.segments, kind)
          }
        }
    }
  }

  return if (nameMatches(nameToMatch, params.queryExecutor)) {
    listOf(this.withMatchedName(nameToMatch))
  }
  else {
    emptyList()
  }
}

fun PolySymbol.toCodeCompletionItems(
  name: String,
  params: PolySymbolCodeCompletionQueryParams,
  stack: PolySymbolQueryStack,
): List<PolySymbolCodeCompletionItem> =
  (this as? PolySymbolWithPattern)?.pattern?.let { pattern ->
    stack.withSymbols(queryScope) {
      pattern.complete(this, stack, name, params)
        .applyIcons(this)
    }
  }
  ?: params.queryExecutor.namesProvider
    .getNames(qualifiedName, PolySymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
    .map { PolySymbolCodeCompletionItem.create(it, 0, symbol = this) }

fun PolySymbol.nameMatches(name: String, queryExecutor: PolySymbolQueryExecutor): Boolean {
  val queryNames = queryExecutor.namesProvider.getNames(kind.withName(name), PolySymbolNamesProvider.Target.NAMES_QUERY)
  val symbolNames = queryExecutor.namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_MAP_STORAGE).toSet()
  return queryNames.any { symbolNames.contains(it) }
}

val PolySymbol.qualifiedName: PolySymbolQualifiedName
  get() = kind.withName(name)

val PolySymbol.namespace: PolySymbolNamespace
  get() = kind.namespace

val PolySymbol.kindName: PolySymbolKindName
  get() = kind.kindName

val PolySymbol.completeMatch: Boolean
  get() = this !is PolySymbolMatch
          || (nameSegments.all { segment -> segment.problem == null && segment.symbols.all { it.completeMatch } }
              && (nameSegments.lastOrNull()?.end ?: 0) == matchedNameOrName.length)

val PolySymbol.nameSegments: List<PolySymbolNameSegment>
  get() = (this as? CompositePolySymbol)?.nameSegments
          ?: (this as? PolySymbolWithPattern)?.pattern?.let { listOf(PolySymbolNameSegment.create(0, 0, this)) }
          ?: listOf(PolySymbolNameSegment.create(this))

val PolySymbol.nameSegmentsWithProblems: Sequence<PolySymbolNameSegment>
  get() =
    Sequence {
      object : Iterator<PolySymbolNameSegment> {
        private var next: PolySymbolNameSegment? = null
        val fifo = LinkedList<PolySymbolNameSegment>()
        val visitedSymbols = mutableSetOf<PolySymbol>()

        init {
          addNameSegmentsToQueue(this@nameSegmentsWithProblems)
          advance()
        }

        private fun addNameSegmentsToQueue(symbol: PolySymbol) {
          if (symbol is CompositePolySymbol && visitedSymbols.add(symbol)) {
            fifo.addAll(symbol.nameSegments)
          }
        }

        private fun advance() {
          while (fifo.isNotEmpty()) {
            val segment = fifo.removeFirst()
            segment.symbols.forEach {
              addNameSegmentsToQueue(it)
            }
            if (segment.problem != null) {
              next = segment
              return
            }
          }
          next = null
        }

        override fun hasNext(): Boolean =
          next != null

        override fun next(): PolySymbolNameSegment =
          next!!.also { advance() }
      }
    }

internal val PolySymbol.matchedNameOrName: String
  get() = (this as? PolySymbolMatch)?.matchedName ?: name

val PolySymbol.hideFromCompletion: Boolean
  get() = this[HideFromCompletionProperty] == true

val (PolySymbolNameSegment.MatchProblem?).isCritical: Boolean
  get() = this == PolySymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART || this == PolySymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL

fun List<PolySymbolNameSegment>.withOffset(offset: Int): List<PolySymbolNameSegment> =
  if (offset != 0) map { it.withOffset(offset) }
  else this

fun PolySymbolApiStatus?.coalesceWith(other: PolySymbolApiStatus?): PolySymbolApiStatus =
  when (this) {
    null -> other ?: PolySymbolApiStatus.Stable
    is PolySymbolApiStatus.Obsolete -> this
    is PolySymbolApiStatus.Deprecated -> when (other) {
      is PolySymbolApiStatus.Obsolete -> other
      else -> this
    }
    is PolySymbolApiStatus.Experimental -> when (other) {
      is PolySymbolApiStatus.Obsolete,
      is PolySymbolApiStatus.Deprecated,
        -> other
      else -> this
    }
    is PolySymbolApiStatus.Stable -> when (other) {
      is PolySymbolApiStatus.Obsolete,
      is PolySymbolApiStatus.Deprecated,
      is PolySymbolApiStatus.Experimental,
        -> other
      else -> this
    }
  }

fun <T : Any> coalesceApiStatus(collection: Iterable<T>?, mapper: (T) -> PolySymbolApiStatus?): PolySymbolApiStatus =
  coalesceApiStatus(collection?.asSequence(), mapper)


fun <T : Any> coalesceApiStatus(sequence: Sequence<T>?, mapper: (T) -> PolySymbolApiStatus?): PolySymbolApiStatus =
  sequence?.map(mapper)?.reduceOrNull { a, b -> a.coalesceWith(b) } ?: PolySymbolApiStatus.Stable

fun Sequence<PolySymbolHtmlAttributeValue?>.merge(): PolySymbolHtmlAttributeValue? {
  var kind: PolySymbolHtmlAttributeValue.Kind? = null
  var type: PolySymbolHtmlAttributeValue.Type? = null
  var required: Boolean? = null
  var default: String? = null
  var langType: Any? = null

  for (value in this) {
    if (value == null) continue
    if (kind == null || kind == PolySymbolHtmlAttributeValue.Kind.PLAIN) {
      kind = value.kind
    }
    if (type == null) {
      type = value.type
    }
    if (required == null) {
      required = value.required
    }
    if (default == null) {
      default = value.default
    }
    if (langType == null) {
      langType = value.langType
    }
  }
  return if (kind != null
             || type != null
             || required != null
             || langType != null
             || default != null)
    PolySymbolHtmlAttributeValue.create(kind, type, required, default, langType)
  else null
}

fun NavigationTarget.createPsiRangeNavigationItem(element: PsiElement, offsetWithinElement: Int): Navigatable {
  val vf = element.containingFile.virtualFile
           ?: return EmptyNavigatable.INSTANCE
  val targetPresentation = this.computePresentation()
  val descriptor = OpenFileDescriptor(
    element.project, vf, element.textRange.startOffset + offsetWithinElement)

  return object : NavigationItem, ItemPresentation {
    override fun navigate(requestFocus: Boolean) {
      descriptor.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = descriptor.canNavigate()

    override fun canNavigateToSource(): Boolean = descriptor.canNavigateToSource()

    override fun getName(): String = targetPresentation.presentableText

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String = targetPresentation.presentableText

    override fun getIcon(unused: Boolean): Icon? = targetPresentation.icon

    override fun getLocationString(): String? {
      val container = targetPresentation.containerText
      val location = targetPresentation.locationText
      return if (container != null || location != null) {
        sequenceOf(container, location).joinToString(", ", "(", ")")
      }
      else null
    }

    override fun toString(): String =
      descriptor.file.name + " [" + descriptor.offset + "]"

  }
}

fun PolySymbolScope.getDefaultCodeCompletions(
  qualifiedName: PolySymbolQualifiedName,
  params: PolySymbolCodeCompletionQueryParams,
  stack: PolySymbolQueryStack,
): List<PolySymbolCodeCompletionItem> =
  getSymbols(qualifiedName.kind,
             PolySymbolListSymbolsQueryParams.create(
               params.queryExecutor,
               expandPatterns = false) {
               copyFiltersFrom(params)
             }, stack)
    .flatMap { it.toCodeCompletionItems(qualifiedName.name, params, stack) }

@ApiStatus.Internal
fun createModificationTracker(trackersPointers: List<Pointer<out ModificationTracker>>): ModificationTracker =
  ModificationTracker {
    var modCount = 0L
    for (tracker in trackersPointers) {
      modCount += (tracker.dereference() ?: return@ModificationTracker -1)
        .modificationCount.also { if (it < 0) return@ModificationTracker -1 }
    }
    modCount
  }

@ApiStatus.Internal
fun acceptSymbolForPsiSourcedPolySymbolRenameHandler(symbol: Symbol): Boolean =
  symbol is PsiSourcedPolySymbol
  && symbol.source is PsiNamedElement
  && symbol.source !is SyntheticElement