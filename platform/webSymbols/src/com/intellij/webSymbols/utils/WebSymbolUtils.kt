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
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.impl.sortSymbolsByPriority
import com.intellij.webSymbols.patterns.impl.applyIcons
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import java.util.*
import javax.swing.Icon
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

val Project.psiModificationCount get() = PsiModificationTracker.getInstance(this).modificationCount

@OptIn(ExperimentalContracts::class)
inline fun <T : Any, P : Any> T.applyIfNotNull(param: P?, block: T.(P) -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  return if (param != null)
    block(this, param)
  else this
}

fun List<WebSymbol>.hasOnlyExtensions(): Boolean =
  all { it.extension }

fun List<WebSymbol>.asSingleSymbol(force: Boolean = false): WebSymbol? =
  if (isEmpty())
    null
  else if (size == 1)
    this[0]
  else {
    val first = this[0]
    if (!force && any { it.namespace != first.namespace || it.kind != first.kind })
      null
    else
      WebSymbolMatch.create(first.name, listOf(WebSymbolNameSegment(0, first.name.length, sortSymbolsByPriority())),
                            first.namespace, first.kind, first.origin)
  }

fun WebSymbol.withMatchedName(matchedName: String) =
  if (matchedName != name) {
    val nameSegment = if (this is WebSymbolMatch && nameSegments.size == 1)
      nameSegments[0].withRange(0, matchedName.length)
    else
      WebSymbolNameSegment(0, matchedName.length, this)
    WebSymbolMatch.create(matchedName, listOf(nameSegment), namespace, kind, origin)
  }
  else this

fun WebSymbol.withNavigationTarget(target: PsiElement): WebSymbol =
  object : WebSymbolDelegate<WebSymbol>(this@withNavigationTarget) {
    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
      listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(target))

    override fun createPointer(): Pointer<out WebSymbol> {
      val symbolPtr = delegate.createPointer()
      val targetPtr = target.createSmartPointer()
      return Pointer {
        targetPtr.dereference()?.let { symbolPtr.dereference()?.withNavigationTarget(it) }
      }
    }
  }

fun WebSymbol.unwrapMatchedSymbols(): Sequence<WebSymbol> =
  Sequence {
    object : Iterator<WebSymbol> {
      private var next: WebSymbol? = null
      val fifo = LinkedList<WebSymbol>()

      init {
        fifo.addLast(this@unwrapMatchedSymbols)
        advance()
      }

      private fun advance() {
        while (fifo.isNotEmpty()) {
          val symbol = fifo.removeFirst()
          if (symbol is WebSymbolMatch) {
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

      override fun next(): WebSymbol =
        next!!.also { advance() }
    }
  }

fun WebSymbol.match(nameToMatch: String,
                    params: WebSymbolsNameMatchQueryParams,
                    context: Stack<WebSymbolsScope>): List<WebSymbol> {
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
            WebSymbolMatch.create(nameToMatch, matchResult.segments,
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

fun WebSymbol.toCodeCompletionItems(name: String,
                                    params: WebSymbolsCodeCompletionQueryParams,
                                    context: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
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
    .getNames(WebSymbolQualifiedName(namespace, kind, this.name), WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
    .map { WebSymbolCodeCompletionItem.create(it, 0, symbol = this) }

fun WebSymbol.nameMatches(name: String, queryExecutor: WebSymbolsQueryExecutor): Boolean {
  val queryNames = queryExecutor.namesProvider.getNames(
    WebSymbolQualifiedName(this.namespace, this.kind, name), WebSymbolNamesProvider.Target.NAMES_QUERY)
  val symbolNames = queryExecutor.namesProvider.getNames(
    WebSymbolQualifiedName(this.namespace, this.kind, this.name), WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE).toSet()
  return queryNames.any { symbolNames.contains(it) }
}

val WebSymbol.qualifiedName: WebSymbolQualifiedName
  get() = WebSymbolQualifiedName(namespace, kind, name)

val WebSymbol.qualifiedKind: WebSymbolQualifiedKind
  get() = WebSymbolQualifiedKind(namespace, kind)

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

val WebSymbol.completeMatch: Boolean
  get() = this !is WebSymbolMatch
          || (nameSegments.all { segment -> segment.problem == null && segment.symbols.all { it.completeMatch } }
              && (nameSegments.lastOrNull()?.end ?: 0) == matchedNameOrName.length)

val WebSymbol.nameSegments: List<WebSymbolNameSegment>
  get() = (this as? CompositeWebSymbol)?.nameSegments
          ?: pattern?.let { listOf(WebSymbolNameSegment(0, 0, this)) }
          ?: listOf(WebSymbolNameSegment(this))

val WebSymbol.nameSegmentsWithProblems: Sequence<WebSymbolNameSegment>
  get() =
    Sequence {
      object : Iterator<WebSymbolNameSegment> {
        private var next: WebSymbolNameSegment? = null
        val fifo = LinkedList<WebSymbolNameSegment>()
        val visitedSymbols = mutableSetOf<WebSymbol>()

        init {
          addNameSegmentsToQueue(this@nameSegmentsWithProblems)
          advance()
        }

        private fun addNameSegmentsToQueue(symbol: WebSymbol) {
          if (symbol is CompositeWebSymbol && visitedSymbols.add(symbol)) {
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

internal val WebSymbol.matchedNameOrName: String
  get() = (this as? WebSymbolMatch)?.matchedName ?: name

val WebSymbol.hideFromCompletion
  get() =
    properties[WebSymbol.PROP_HIDE_FROM_COMPLETION] == true

val (WebSymbolNameSegment.MatchProblem?).isCritical
  get() = this == WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART || this == WebSymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL

fun List<WebSymbolNameSegment>.withOffset(offset: Int): List<WebSymbolNameSegment> =
  if (offset != 0) map { it.withOffset(offset) }
  else this

fun WebSymbolApiStatus?.coalesceWith(other: WebSymbolApiStatus?): WebSymbolApiStatus =
  when (this) {
    null -> other ?: WebSymbolApiStatus.Stable
    is WebSymbolApiStatus.Obsolete -> this
    is WebSymbolApiStatus.Deprecated -> when (other) {
      is WebSymbolApiStatus.Obsolete -> other
      else -> this
    }
    is WebSymbolApiStatus.Experimental -> when (other) {
      is WebSymbolApiStatus.Obsolete,
      is WebSymbolApiStatus.Deprecated -> other
      else -> this
    }
    is WebSymbolApiStatus.Stable -> when (other) {
      is WebSymbolApiStatus.Obsolete,
      is WebSymbolApiStatus.Deprecated,
      is WebSymbolApiStatus.Experimental -> other
      else -> this
    }
  }

fun <T : Any> coalesceApiStatus(collection: Iterable<T>?, mapper: (T) -> WebSymbolApiStatus?): WebSymbolApiStatus =
  coalesceApiStatus(collection?.asSequence(), mapper)


fun <T : Any> coalesceApiStatus(sequence: Sequence<T>?, mapper: (T) -> WebSymbolApiStatus?): WebSymbolApiStatus =
  sequence?.map(mapper)?.reduceOrNull { a, b -> a.coalesceWith(b) } ?: WebSymbolApiStatus.Stable

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

fun WebSymbolsScope.getDefaultCodeCompletions(qualifiedName: WebSymbolQualifiedName,
                                              params: WebSymbolsCodeCompletionQueryParams,
                                              scope: Stack<WebSymbolsScope>) =
  getSymbols(qualifiedName.qualifiedKind,
             WebSymbolsListSymbolsQueryParams(
               params.queryExecutor,
               expandPatterns = false,
               virtualSymbols = params.virtualSymbols
             ), scope)
    .flatMap { (it as? WebSymbol)?.toCodeCompletionItems(qualifiedName.name, params, scope) ?: emptyList() }

internal val List<WebSymbolsScope>.lastWebSymbol: WebSymbol?
  get() = this.lastOrNull { it is WebSymbol } as? WebSymbol

internal fun createModificationTracker(trackersPointers: List<Pointer<out ModificationTracker>>): ModificationTracker =
  ModificationTracker {
    var modCount = 0L
    for (tracker in trackersPointers) {
      modCount += (tracker.dereference() ?: return@ModificationTracker -1)
        .modificationCount.also { if (it < 0) return@ModificationTracker -1 }
    }
    modCount
  }