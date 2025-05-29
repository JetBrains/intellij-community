// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WebSymbolUtils")

package com.intellij.webSymbols.utils

import com.intellij.model.Pointer
import com.intellij.navigation.EmptyNavigatable
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.asSafely
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.impl.WebSymbolNameSegmentImpl
import com.intellij.webSymbols.impl.sortSymbolsByPriority
import com.intellij.webSymbols.impl.withOffset
import com.intellij.webSymbols.impl.withRange
import com.intellij.webSymbols.patterns.impl.applyIcons
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.query.impl.PolySymbolMatchImpl
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import org.jetbrains.annotations.ApiStatus
import java.util.*
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
    if (!force && any { it.namespace != first.namespace || it.kind != first.kind })
      null
    else
      PolySymbolMatch.create(first.name, first.qualifiedKind, first.origin,
                             WebSymbolNameSegment.create(0, first.name.length, sortSymbolsByPriority()))
  }

fun PolySymbol.withMatchedName(matchedName: String): PolySymbol =
  if (matchedName != name) {
    val nameSegment = if (this is PolySymbolMatch && nameSegments.size == 1)
      nameSegments[0].withRange(0, matchedName.length)
    else
      WebSymbolNameSegment.create(0, matchedName.length, this)
    PolySymbolMatch.create(matchedName, qualifiedKind, origin, nameSegment)
  }
  else this

fun PolySymbol.withMatchedKind(qualifiedKind: PolySymbolQualifiedKind): PolySymbol =
  if (qualifiedKind != this.qualifiedKind) {
    val matchedName = this.asSafely<PolySymbolMatch>()?.matchedName ?: name
    val nameSegment = if (this is PolySymbolMatch && nameSegments.size == 1)
      nameSegments[0].withRange(0, matchedName.length)
    else
      WebSymbolNameSegment.create(0, matchedName.length, this)
    PolySymbolMatch.create(matchedName, qualifiedKind, origin, nameSegment)
  }
  else this

fun PolySymbol.withNavigationTarget(target: PsiElement): PolySymbol =
  object : PolySymbolDelegate<PolySymbol>(this@withNavigationTarget) {
    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
      listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(target))

    override fun createPointer(): Pointer<out PolySymbol> {
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

fun WebSymbolNameSegment.withSymbols(symbols: List<PolySymbol>): WebSymbolNameSegment =
  (this as WebSymbolNameSegmentImpl).withSymbols(symbols)

fun PolySymbolMatch.withSegments(segments: List<WebSymbolNameSegment>): PolySymbolMatch =
  (this as PolySymbolMatchImpl).withSegments(segments)

fun PolySymbol.match(
  nameToMatch: String,
  params: WebSymbolsNameMatchQueryParams,
  context: Stack<PolySymbolsScope>,
): List<PolySymbol> {
  pattern?.let { pattern ->
    context.push(this)
    try {
      return pattern
        .match(this, context, nameToMatch, params)
        .mapNotNull { matchResult ->
          if ((matchResult.segments.lastOrNull()?.end ?: 0) < nameToMatch.length) {
            null
          }
          else {
            PolySymbolMatch.create(nameToMatch, matchResult.segments,
                                   namespace, kind, origin)
          }
        }
    }
    finally {
      context.pop()
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
  params: WebSymbolsCodeCompletionQueryParams,
  context: Stack<PolySymbolsScope>,
): List<WebSymbolCodeCompletionItem> =
  pattern?.let { pattern ->
    context.push(this)
    try {
      pattern.complete(this, context, name, params)
        .applyIcons(this)
    }
    finally {
      context.pop()
    }
  }
  ?: params.queryExecutor.namesProvider
    .getNames(PolySymbolQualifiedName(namespace, kind, this.name), WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
    .map { WebSymbolCodeCompletionItem.create(it, 0, symbol = this) }

fun PolySymbol.nameMatches(name: String, queryExecutor: WebSymbolsQueryExecutor): Boolean {
  val queryNames = queryExecutor.namesProvider.getNames(
    PolySymbolQualifiedName(this.namespace, this.kind, name), WebSymbolNamesProvider.Target.NAMES_QUERY)
  val symbolNames = queryExecutor.namesProvider.getNames(
    PolySymbolQualifiedName(this.namespace, this.kind, this.name), WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE).toSet()
  return queryNames.any { symbolNames.contains(it) }
}

val PolySymbol.qualifiedName: PolySymbolQualifiedName
  get() = PolySymbolQualifiedName(namespace, kind, name)

val PolySymbol.qualifiedKind: PolySymbolQualifiedKind
  get() = PolySymbolQualifiedKind(namespace, kind)

fun WebSymbolNameSegment.getProblemKind(): ProblemKind? =
  when (problem) {
    WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART -> ProblemKind.MissingRequiredPart
    WebSymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL ->
      if (start == end)
        ProblemKind.MissingRequiredPart
      else
        ProblemKind.UnknownSymbol
    WebSymbolNameSegment.MatchProblem.DUPLICATE -> ProblemKind.DuplicatedPart
    null -> null
  }

val PolySymbol.completeMatch: Boolean
  get() = this !is PolySymbolMatch
          || (nameSegments.all { segment -> segment.problem == null && segment.symbols.all { it.completeMatch } }
              && (nameSegments.lastOrNull()?.end ?: 0) == matchedNameOrName.length)

val PolySymbol.nameSegments: List<WebSymbolNameSegment>
  get() = (this as? CompositePolySymbol)?.nameSegments
          ?: pattern?.let { listOf(WebSymbolNameSegment.create(0, 0, this)) }
          ?: listOf(WebSymbolNameSegment.create(this))

val PolySymbol.nameSegmentsWithProblems: Sequence<WebSymbolNameSegment>
  get() =
    Sequence {
      object : Iterator<WebSymbolNameSegment> {
        private var next: WebSymbolNameSegment? = null
        val fifo = LinkedList<WebSymbolNameSegment>()
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

        override fun next(): WebSymbolNameSegment =
          next!!.also { advance() }
      }
    }

internal val PolySymbol.matchedNameOrName: String
  get() = (this as? PolySymbolMatch)?.matchedName ?: name

val PolySymbol.hideFromCompletion: Boolean
  get() =
    properties[PolySymbol.PROP_HIDE_FROM_COMPLETION] == true

val (WebSymbolNameSegment.MatchProblem?).isCritical: Boolean
  get() = this == WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART || this == WebSymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL

fun List<WebSymbolNameSegment>.withOffset(offset: Int): List<WebSymbolNameSegment> =
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

fun Sequence<WebSymbolHtmlAttributeValue?>.merge(): WebSymbolHtmlAttributeValue? {
  var kind: WebSymbolHtmlAttributeValue.Kind? = null
  var type: WebSymbolHtmlAttributeValue.Type? = null
  var required: Boolean? = null
  var default: String? = null
  var langType: Any? = null

  for (value in this) {
    if (value == null) continue
    if (kind == null || kind == WebSymbolHtmlAttributeValue.Kind.PLAIN) {
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
    WebSymbolHtmlAttributeValue.create(kind, type, required, default, langType)
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

fun PolySymbolsScope.getDefaultCodeCompletions(
  qualifiedName: PolySymbolQualifiedName,
  params: WebSymbolsCodeCompletionQueryParams,
  scope: Stack<PolySymbolsScope>,
): List<WebSymbolCodeCompletionItem> =
  getSymbols(qualifiedName.qualifiedKind,
             WebSymbolsListSymbolsQueryParams.create(
               params.queryExecutor,
               expandPatterns = false,
               virtualSymbols = params.virtualSymbols
             ), scope)
    .flatMap { (it as? PolySymbol)?.toCodeCompletionItems(qualifiedName.name, params, scope) ?: emptyList() }

internal val List<PolySymbolsScope>.lastPolySymbol: PolySymbol?
  get() = this.lastOrNull { it is PolySymbol } as? PolySymbol

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