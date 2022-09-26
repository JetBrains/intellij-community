// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbolsContainer.Companion.NAMESPACE_CSS
import com.intellij.webSymbols.WebSymbolsContainer.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.WebSymbolsContainer.Companion.NAMESPACE_JS
import com.intellij.webSymbols.WebSymbolsScope.Companion.applyScope
import com.intellij.webSymbols.utils.hideFromCompletion
import java.util.*
import kotlin.math.max
import kotlin.math.min

internal class WebSymbolsRegistryImpl(private val rootContext: List<WebSymbolsContainer>,
                                      override val namesProvider: WebSymbolNamesProvider,
                                      override val scope: WebSymbolsScope,
                                      override val framework: FrameworkId?,
                                      override val allowResolve: Boolean) : WebSymbolsRegistry {

  override fun hashCode(): Int =
    Objects.hash(rootContext, framework, namesProvider, scope)

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is WebSymbolsRegistryImpl
    && other.framework == framework
    && other.rootContext == rootContext
    && other.namesProvider == namesProvider
    && other.scope == scope

  override fun createPointer(): Pointer<WebSymbolsRegistry> {
    val namesProviderPtr = this.namesProvider.createPointer()
    val framework = this.framework
    val allowResolve = this.allowResolve
    val scopePtr = this.scope.createPointer()
    val rootContextPointers = this.rootContext.map { it.createPointer() }
    return Pointer<WebSymbolsRegistry> {
      @Suppress("UNCHECKED_CAST")
      val rootContext = rootContextPointers.map { it.dereference() }
                          .takeIf { it.all { c -> c != null } } as? List<WebSymbolsContainer>
                        ?: return@Pointer null

      val namesProvider = namesProviderPtr.dereference()
                          ?: return@Pointer null

      val scope = scopePtr.dereference()
                  ?: return@Pointer null
      WebSymbolsRegistryImpl(rootContext, namesProvider, scope, framework, allowResolve)
    }
  }

  override fun runNameMatchQuery(path: List<String>,
                                 virtualSymbols: Boolean,
                                 abstractSymbols: Boolean,
                                 strictScope: Boolean,
                                 context: List<WebSymbolsContainer>): List<WebSymbol> =
    runNameMatchQuery(path, WebSymbolsNameMatchQueryParams(this, virtualSymbols, abstractSymbols, strictScope), context)

  override fun runCodeCompletionQuery(path: List<String>,
                                      position: Int,
                                      virtualSymbols: Boolean,
                                      context: List<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(path, WebSymbolsCodeCompletionQueryParams(this, position, virtualSymbols), context)

  override fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): WebSymbolsRegistry =
    if (rules.isEmpty())
      this
    else
      WebSymbolsRegistryImpl(rootContext, namesProvider.withRules(rules), scope, framework, allowResolve)

  internal fun runNameMatchQuery(path: List<String>, queryParams: WebSymbolsNameMatchQueryParams,
                                 context: List<WebSymbolsContainer>): List<WebSymbol> =
    runQuery(path, queryParams, context) { finalContext: Collection<WebSymbolsContainer>,
                                           pathSection: PathSection,
                                           params: WebSymbolsNameMatchQueryParams ->
      val kind = pathSection.kind
      val namespace = pathSection.namespace

      val result = finalContext
        .takeLastUntilExclusiveContainerFor(namespace, kind)
        .asSequence()
        .flatMap {
          it.getSymbols(namespace, kind, pathSection.name, params, Stack(finalContext))
        }
        .filterIsInstance<WebSymbol>()
        .filter { it.nameSegments.size > 1 || (it.nameSegments.isNotEmpty() && it.nameSegments[0].problem == null) }
        .distinct()
        .toList()
        .let {
          if (it.isNotEmpty())
            it.applyScope(scope, params.strictScope, namespace, kind, pathSection.name)
          else it
        }
        .let {
          if (it.isNotEmpty() && pathSection.name != null)
            it.selectBest(WebSymbol::nameSegments, WebSymbol::priority, WebSymbol::extension)
          else it
        }
      result
    }

  internal fun runCodeCompletionQuery(path: List<String>, queryParams: WebSymbolsCodeCompletionQueryParams,
                                      context: List<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    runQuery(path, queryParams, context) { finalContext: Collection<WebSymbolsContainer>,
                                           pathSection: PathSection,
                                           params: WebSymbolsCodeCompletionQueryParams ->
      var proximityBase = 0
      var nextProximityBase = 0
      var previousName: String? = null
      val pos = params.position
      val result = finalContext
        .takeLastUntilExclusiveContainerFor(pathSection.namespace, pathSection.kind)
        .asSequence()
        .flatMap { container ->
          if (container !is WebSymbol || !container.extension || container.matchedName != previousName) {
            previousName = (container as? WebSymbol)?.matchedName
            proximityBase = nextProximityBase
          }
          container.getCodeCompletions(pathSection.namespace, pathSection.kind, pathSection.name, params, Stack(finalContext))
            .mapNotNull { item ->
              if (item.offset > pos || item.symbol?.hideFromCompletion == true)
                return@mapNotNull null
              val newProximity = (item.proximity ?: 0) + proximityBase
              if (newProximity + 1 > nextProximityBase) {
                // Increase proximity base for next container, but allow to exceed it
                nextProximityBase = min(proximityBase + 5, newProximity) + 1
              }
              item.withProximity(newProximity)
            }
        }
        .mapWithSymbolPriority()
        .mapNotNull { it.applyScope(scope, pathSection.namespace, pathSection.kind) }
        .toList()
        .sortAndDeduplicate()
      result
    }


  override fun getModificationCount(): Long =
    rootContext.sumOf { it.modificationCount } + namesProvider.modificationCount + scope.modificationCount

  private fun <T, P : WebSymbolsRegistryQueryParams> runQuery(
    path: List<String>,
    params: P,
    initialContext: List<WebSymbolsContainer>,
    finalProcessor: (
      context: Collection<WebSymbolsContainer>,
      pathSection: PathSection,
      params: P,
    ) -> List<T>): List<T> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val sections = parsePath(path)
    if (sections.isEmpty()) return emptyList()

    val context = rootContext.toMutableSet()
    initialContext.flatMapTo(context) {
      if (it is WebSymbol)
        it.contextContainers
      else
        sequenceOf(it)
    }
    return RecursionManager.doPreventingRecursion(Pair(sections, params.virtualSymbols), false) {
      val contextQueryParams = WebSymbolsNameMatchQueryParams(this, true, false)
      var i = 0
      while (i < sections.size - 1) {
        val section = sections[i++]
        val contextSymbols = context.flatMap {
          it.getSymbols(section.namespace, section.kind, section.name, contextQueryParams, Stack(context))
        }
        contextSymbols.flatMapTo(context) {
          if (it is WebSymbol)
            it.contextContainers
          else
            sequenceOf(it)
        }
      }
      val lastSection = sections.last()
      finalProcessor(context, lastSection, params)
    } ?: run {
      thisLogger().warn("Recursive Web Symbols query: ${path.joinToString("/", "/")} with virtualSymbols=${params.virtualSymbols}.\n" +
                        "Context: " + initialContext.filterIsInstance<WebSymbol>().map { it.kind + "/" + it.name })
      emptyList()
    }
  }

  internal data class PathSection(val namespace: SymbolNamespace?, val kind: String, val name: String?)

  private fun Collection<WebSymbolsContainer>.takeLastUntilExclusiveContainerFor(namespace: SymbolNamespace?,
                                                                                 kind: String): List<WebSymbolsContainer> =
    toList()
      .let { list ->
        list.subList(max(0, list.indexOfLast { it.isExclusiveFor(namespace, kind) }), list.size)
      }

  private fun List<WebSymbolCodeCompletionItem>.sortAndDeduplicate(): List<WebSymbolCodeCompletionItem> =
    groupBy { Triple(it.name, it.displayName, it.offset) }
      .mapNotNull { (_, items) ->
        if (items.size == 1) {
          items[0]
        }
        else {
          items
            .sortedWith(Comparator.comparing { it: WebSymbolCodeCompletionItem -> -(it.priority ?: WebSymbol.Priority.NORMAL).ordinal }
                          .thenComparingInt { -(it.proximity ?: 0) })
            .firstOrNull()
        }
      }

  private fun Sequence<WebSymbolCodeCompletionItem>.mapWithSymbolPriority() =
    map { item ->
      item.symbol
        ?.priority
        ?.takeIf { it > (item.priority ?: WebSymbol.Priority.LOWEST) }
        ?.let { item.withPriority(it) }
      ?: item
    }

  companion object {

    internal fun String.asSymbolNamespace(): SymbolNamespace? =
      takeIf { it == NAMESPACE_JS || it == NAMESPACE_HTML || it == NAMESPACE_CSS }

    internal fun parsePath(path: String?, context: SymbolNamespace? = null): List<PathSection> =
      if (path != null)
        parsePath(StringUtil.split(path, "/", true, true), context)
      else
        emptyList()

    internal fun parsePath(path: List<String>, context: SymbolNamespace? = null): List<PathSection> {
      var i = 0
      var prevRoot: SymbolNamespace? = context
      val result = mutableListOf<PathSection>()
      while (i < path.size) {
        var root = path[i].asSymbolNamespace()
        if (root != null) {
          i++
          prevRoot = root
        }
        else {
          root = prevRoot
        }
        if (i >= path.size) break
        val kind = path[i++]
        val name = if (i >= path.size) null
        else path[i++]
        result.add(PathSection(root, kind, name))
      }
      return result
    }
  }
}

