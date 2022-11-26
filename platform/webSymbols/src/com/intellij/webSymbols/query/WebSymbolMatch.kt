// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Priority
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationTarget
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.utils.merge
import javax.swing.Icon

open class WebSymbolMatch private constructor(override val matchedName: String,
                                              override val nameSegments: List<WebSymbolNameSegment>,
                                              override val namespace: SymbolNamespace,
                                              override val kind: SymbolKind,
                                              override val origin: WebSymbolOrigin,
                                              private val explicitPriority: Priority?,
                                              private val explicitProximity: Int?) : WebSymbol {

  protected fun reversedSegments() = Sequence { ReverseListIterator(nameSegments) }

  override val psiContext: PsiElement?
    get() = reversedSegments().flatMap { it.symbols.asSequence() }
      .mapNotNull { it.psiContext }.firstOrNull()

  override val documentation: WebSymbolDocumentation?
    get() = reversedSegments().flatMap { it.symbols.asSequence() }
      .mapNotNull { it.documentation }.firstOrNull()

  override val name: String
    get() = matchedName.substring(nameSegments.firstOrNull()?.start ?: 0,
                                  nameSegments.lastOrNull()?.end ?: 0)

  override val description: String?
    get() = nameSegments.takeIf { it.size == 1 }
      ?.get(0)?.symbols?.asSequence()?.map { it.description }?.firstOrNull()

  override val docUrl: String?
    get() = nameSegments.takeIf { it.size == 1 }
      ?.get(0)?.symbols?.asSequence()?.map { it.docUrl }?.firstOrNull()

  override val descriptionSections: Map<String, String>
    get() = nameSegments.takeIf { it.size == 1 }
              ?.get(0)?.symbols?.asSequence()
              ?.flatMap { it.descriptionSections.asSequence() }
              ?.distinct()
              ?.associateBy({ it.key }, { it.value })
            ?: emptyMap()

  override val virtual: Boolean
    get() = nameSegments.any { segment -> segment.symbols.any { it.virtual } }

  override val extension: Boolean
    get() = nameSegments.isNotEmpty() && nameSegments.all { segment -> segment.symbols.isNotEmpty() && segment.symbols.all { it.extension } }

  override val priority: Priority?
    get() = explicitPriority ?: reversedSegments().mapNotNull { it.priority }.firstOrNull()

  override val proximity: Int?
    get() = explicitProximity ?: reversedSegments().mapNotNull { it.proximity }.firstOrNull()

  override val completeMatch: Boolean
    get() = (nameSegments.all { segment -> segment.problem == null && segment.symbols.all { it.completeMatch } }
             && (nameSegments.lastOrNull()?.end ?: 0) == matchedName.length)

  override val queryScope: Sequence<WebSymbolsScope>
    get() = nameSegments.asSequence()
      .flatMap { it.symbols }
      .flatMap { it.queryScope }

  override val type: Any?
    get() = reversedSegments().flatMap { it.symbols }
      .mapNotNull { it.type }.firstOrNull()

  override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.attributeValue }.merge()

  override val required: Boolean?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.required }.firstOrNull()

  override val experimental: Boolean
    get() = reversedSegments().flatMap { it.symbols }.map { it.experimental }.firstOrNull() ?: false

  override val deprecated: Boolean
    get() = reversedSegments().map { it.deprecated }.firstOrNull() ?: false

  override val icon: Icon?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.icon }.firstOrNull()

  override val properties: Map<String, Any>
    get() = nameSegments.asSequence().flatMap { it.symbols }
      .flatMap { it.properties.entries }
      .filter { it.key != WebSymbol.PROP_HIDE_FROM_COMPLETION }
      .map { Pair(it.key, it.value) }
      .toMap()

  override fun createPointer(): Pointer<WebSymbolMatch> =
    WebSymbolMatchPointer(this)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (nameSegments.size == 1)
      nameSegments[0].symbols.asSequence()
        .flatMap { it.getNavigationTargets(project) }
        .toList()
    else emptyList()

  override fun getDocumentationTarget(): DocumentationTarget =
    reversedSegments()
      .flatMap { it.symbols.asSequence() }
      .map {
        if (it === this) super.getDocumentationTarget()
        else it.documentationTarget
      }
      .filter { it !is WebSymbolDocumentationTarget || it.symbol.documentation?.isNotEmpty() == true }
      .firstOrNull()
    ?: super.getDocumentationTarget()

  override fun equals(other: Any?): Boolean =
    other is WebSymbolMatch
    && other.name == name
    && other.origin == origin
    && other.namespace == namespace
    && other.kind == kind
    && other.nameSegments.equalsIgnoreOffset(nameSegments)

  override fun hashCode(): Int = name.hashCode()

  class ReverseListIterator<T>(list: List<T>) : Iterator<T> {

    private val iterator = list.listIterator(list.size)

    override operator fun hasNext(): Boolean {
      return iterator.hasPrevious()
    }

    override operator fun next(): T {
      return iterator.previous()
    }

  }

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(matchedName: String,
               nameSegments: List<WebSymbolNameSegment>,
               namespace: SymbolNamespace,
               kind: SymbolKind,
               origin: WebSymbolOrigin,
               explicitPriority: Priority? = null,
               explicitProximity: Int? = null): WebSymbolMatch =
      if (nameSegments.any { it.symbols.any { symbol -> symbol is PsiSourcedWebSymbol } })
        PsiSourcedWebSymbolMatch(matchedName, nameSegments, namespace, kind, origin,
                                 explicitPriority, explicitProximity)
      else WebSymbolMatch(matchedName, nameSegments, namespace, kind, origin,
                          explicitPriority, explicitProximity)

    private fun List<WebSymbolNameSegment>.equalsIgnoreOffset(other: List<WebSymbolNameSegment>): Boolean {
      if (size != other.size) return false
      if (this.isEmpty()) return true
      val startOffset1 = this[0].start
      val startOffset2 = other[0].start

      for (i in indices) {
        val segment1 = this[i]
        val segment2 = other[i]
        if (segment1.start - startOffset1 != segment2.start - startOffset2
            || segment1.end - startOffset1 != segment2.end - startOffset2
            || segment1.deprecated != segment2.deprecated
            || segment1.symbols != segment2.symbols
            || segment1.problem != segment2.problem
            || segment1.displayName != segment2.displayName
            || segment1.priority != segment2.priority
            || segment1.proximity != segment2.proximity) {
          return false
        }
      }
      return true
    }

  }

  class PsiSourcedWebSymbolMatch(matchedName: String,
                                 nameSegments: List<WebSymbolNameSegment>,
                                 namespace: SymbolNamespace,
                                 kind: SymbolKind,
                                 origin: WebSymbolOrigin,
                                 explicitPriority: Priority?,
                                 explicitProximity: Int?)
    : WebSymbolMatch(matchedName, nameSegments, namespace, kind, origin, explicitPriority, explicitProximity), PsiSourcedWebSymbol {

    override val psiContext: PsiElement?
      get() = super<PsiSourcedWebSymbol>.psiContext

    override val source: PsiElement?
      get() = reversedSegments().flatMap { it.symbols.asSequence() }
        .mapNotNull { it.psiContext }.firstOrNull()

    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
      super<WebSymbolMatch>.getNavigationTargets(project)

  }

  private class WebSymbolMatchPointer(webSymbolMatch: WebSymbolMatch) : Pointer<WebSymbolMatch> {

    private val matchedName = webSymbolMatch.matchedName
    private val nameSegments = webSymbolMatch.nameSegments
      .map { it.createPointer() }
    private val namespace = webSymbolMatch.namespace
    private val kind = webSymbolMatch.kind
    private val origin = webSymbolMatch.origin
    private val explicitPriority = webSymbolMatch.explicitPriority
    private val explicitProximity = webSymbolMatch.explicitProximity

    override fun dereference(): WebSymbolMatch? =
      nameSegments.map { it.dereference() }
        .takeIf { it.all { segment -> segment != null } }
        ?.let {
          @Suppress("UNCHECKED_CAST")
          (create(matchedName, it as List<WebSymbolNameSegment>, namespace, kind, origin,
                  explicitPriority, explicitProximity))
        }

  }

}
