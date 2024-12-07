// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Priority
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationTarget
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolMatchBuilder
import com.intellij.webSymbols.refactoring.WebSymbolRenameTarget
import com.intellij.webSymbols.search.WebSymbolSearchTarget
import com.intellij.webSymbols.utils.coalesceApiStatus
import com.intellij.webSymbols.utils.merge
import javax.swing.Icon

internal open class WebSymbolMatchImpl private constructor(
  override val matchedName: String,
  override val nameSegments: List<WebSymbolNameSegment>,
  override val namespace: SymbolNamespace,
  override val kind: SymbolKind,
  override val origin: WebSymbolOrigin,
  private val explicitPriority: Priority?,
  private val explicitProximity: Int?,
  private val additionalProperties: Map<String, Any>,
) : WebSymbolMatch {

  init {
    require(nameSegments.isNotEmpty()) { "nameSegments must not be empty" }
  }

  protected fun reversedSegments() = Sequence { ReverseListIterator(nameSegments) }

  override fun withCustomProperties(properties: Map<String, Any>): WebSymbolMatch =
    create(matchedName, nameSegments, namespace, kind, origin, explicitPriority, explicitProximity, additionalProperties + properties)

  override val psiContext: PsiElement?
    get() = reversedSegments().flatMap { it.symbols.asSequence() }
      .mapNotNull { it.psiContext }.firstOrNull()

  override fun createDocumentation(location: PsiElement?): WebSymbolDocumentation? =
    reversedSegments().flatMap { it.symbols.asSequence() }
      .firstNotNullOfOrNull { it.createDocumentation(location) }

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

  override val queryScope: List<WebSymbolsScope>
    get() = nameSegments.asSequence()
      .flatMap { it.symbols }
      .flatMap { it.queryScope }
      .toList()

  override val type: Any?
    get() = reversedSegments().flatMap { it.symbols }
      .mapNotNull { it.type }.firstOrNull()

  override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.attributeValue }.merge()

  override val required: Boolean?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.required }.firstOrNull()

  override val apiStatus: WebSymbolApiStatus
    get() = coalesceApiStatus(reversedSegments().flatMap { it.symbols }) { it.apiStatus }

  override val icon: Icon?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.icon }.firstOrNull()

  override val properties: Map<String, Any>
    get() = nameSegments.asSequence().flatMap { it.symbols }
      .flatMap { it.properties.entries }
      .filter { it.key != WebSymbol.PROP_HIDE_FROM_COMPLETION }
      .plus(additionalProperties.entries)
      .map { Pair(it.key, it.value) }
      .toMap()

  override fun createPointer(): Pointer<out WebSymbolMatchImpl> =
    WebSymbolMatchPointer<WebSymbolMatchImpl>(this, ::WebSymbolMatchImpl)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (nameSegments.size == 1)
      nameSegments[0].symbols.asSequence()
        .flatMap { it.getNavigationTargets(project) }
        .distinct()
        .toList()
    else emptyList()

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget =
    reversedSegments()
      .flatMap { it.symbols.asSequence() }
      .map {
        if (it === this) super<WebSymbolMatch>.getDocumentationTarget(location)
        else it.getDocumentationTarget(location)
      }
      .filter { it !is WebSymbolDocumentationTarget || it.symbol.createDocumentation(location)?.isNotEmpty() == true }
      .firstOrNull()
    ?: super<WebSymbolMatch>.getDocumentationTarget(location)

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    super<WebSymbolMatch>.isEquivalentTo(symbol)
    || nameSegments.filter { it.start != it.end }
      .let { nonEmptySegments ->
        nonEmptySegments.size == 1
        && nonEmptySegments[0].symbols.any { it.isEquivalentTo(symbol) }
      }

  override val searchTarget: WebSymbolSearchTarget?
    get() = nameSegments.filter { it.start != it.end }
      .takeIf { it.size == 1 }
      ?.get(0)
      ?.symbols
      ?.takeIf { it.size == 1 }
      ?.get(0)
      ?.searchTarget

  override val renameTarget: WebSymbolRenameTarget?
    get() = nameSegments.filter { it.start != it.end }
      .takeIf { it.size == 1 }
      ?.get(0)
      ?.symbols
      ?.takeIf { it.size == 1 }
      ?.get(0)
      ?.renameTarget

  override fun equals(other: Any?): Boolean =
    other is WebSymbolMatch
    && other.name == name
    && other.origin == origin
    && other.namespace == namespace
    && other.kind == kind
    && other.nameSegments.equalsIgnoreOffset(nameSegments)

  override fun hashCode(): Int = name.hashCode()

  internal fun withSegments(segments: List<WebSymbolNameSegment>): WebSymbolMatch =
    create(matchedName, segments, namespace, kind, origin, explicitPriority, explicitProximity, additionalProperties)

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

    private fun create(
      matchedName: String,
      nameSegments: List<WebSymbolNameSegment>,
      namespace: SymbolNamespace,
      kind: SymbolKind,
      origin: WebSymbolOrigin,
      explicitPriority: Priority?,
      explicitProximity: Int?,
      additionalProperties: Map<String, Any>,
    ): WebSymbolMatch =
      if (nameSegments.all { it.start == it.end || (it.symbols.isNotEmpty() && it.symbols.any { symbol -> symbol is PsiSourcedWebSymbol }) })
        PsiSourcedWebSymbolMatch(matchedName, nameSegments, namespace, kind, origin,
                                 explicitPriority, explicitProximity, additionalProperties)
      else WebSymbolMatchImpl(matchedName, nameSegments, namespace, kind, origin,
                              explicitPriority, explicitProximity, additionalProperties)

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
            || segment1.apiStatus != segment2.apiStatus
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

  private class PsiSourcedWebSymbolMatch(
    matchedName: String,
    nameSegments: List<WebSymbolNameSegment>,
    namespace: SymbolNamespace,
    kind: SymbolKind,
    origin: WebSymbolOrigin,
    explicitPriority: Priority?,
    explicitProximity: Int?,
    additionalProperties: Map<String, Any>,
  ) : WebSymbolMatchImpl(matchedName, nameSegments, namespace, kind, origin, explicitPriority, explicitProximity, additionalProperties),
      PsiSourcedWebSymbol {

    override val psiContext: PsiElement?
      get() = reversedSegments().flatMap { it.symbols.asSequence() }
        .mapNotNull { it.psiContext }.firstOrNull()

    override val source: PsiElement?
      get() = reversedSegments().flatMap { it.symbols }
        .mapNotNull { (it as? PsiSourcedWebSymbol)?.source }.singleOrNull()

    override fun createPointer(): Pointer<PsiSourcedWebSymbolMatch> =
      WebSymbolMatchPointer<PsiSourcedWebSymbolMatch>(this, ::PsiSourcedWebSymbolMatch)

    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
      super<WebSymbolMatchImpl>.getNavigationTargets(project)

    override fun isEquivalentTo(symbol: Symbol): Boolean =
      super<WebSymbolMatchImpl>.isEquivalentTo(symbol)

  }

  class BuilderImpl(
    private var matchedName: String,
    private var qualifiedKind: WebSymbolQualifiedKind,
    private var origin: WebSymbolOrigin,
  ) : WebSymbolMatchBuilder {

    private var nameSegments = mutableListOf<WebSymbolNameSegment>()
    private var properties = mutableMapOf<String, Any>()
    private var explicitPriority: Priority? = null
    private var explicitProximity: Int? = null

    fun build(): WebSymbolMatch =
      create(matchedName, nameSegments, qualifiedKind.namespace, qualifiedKind.kind,
             origin, explicitPriority, explicitProximity, properties)

    override fun addNameSegments(value: List<WebSymbolNameSegment>): WebSymbolMatchBuilder = this.also {
      nameSegments.addAll(value)
    }

    override fun addNameSegments(vararg value: WebSymbolNameSegment): WebSymbolMatchBuilder = this.also {
      nameSegments.addAll(value)
    }

    override fun addNameSegment(value: WebSymbolNameSegment): WebSymbolMatchBuilder = this.also {
      nameSegments.add(value)
    }

    override fun explicitPriority(value: Priority): WebSymbolMatchBuilder = this.also {
      explicitPriority = value
    }

    override fun explicitProximity(value: Int): WebSymbolMatchBuilder = this.also {
      explicitProximity = value
    }

    override fun setProperty(name: String, value: Any): WebSymbolMatchBuilder = this.also {
      properties[name] = value
    }
  }

  private class WebSymbolMatchPointer<T : WebSymbolMatch>(
    webSymbolMatch: WebSymbolMatchImpl,
    private val newInstanceProvider: (
      matchedName: String,
      nameSegments: List<WebSymbolNameSegment>,
      namespace: SymbolNamespace,
      kind: SymbolKind,
      origin: WebSymbolOrigin,
      explicitPriority: Priority?,
      explicitProximity: Int?,
      additionalProperties: Map<String, Any>,
    ) -> T,
  ) : Pointer<T> {

    private val matchedName = webSymbolMatch.matchedName
    private val nameSegments = webSymbolMatch.nameSegments
      .map { it.createPointer() }
    private val namespace = webSymbolMatch.namespace
    private val kind = webSymbolMatch.kind
    private val origin = webSymbolMatch.origin
    private val explicitPriority = webSymbolMatch.explicitPriority
    private val explicitProximity = webSymbolMatch.explicitProximity
    private val additionalProperties = webSymbolMatch.additionalProperties
      .createPointers()

    override fun dereference(): T? =
      nameSegments.map { it.dereference() }
        .takeIf { it.all { segment -> segment != null } }
        ?.let {
          var dereferencingProblems = Ref(false)
          val dereferencedProperties = additionalProperties.dereferencePointers(dereferencingProblems)
          if (dereferencingProblems.get()) return null

          @Suppress("UNCHECKED_CAST")
          newInstanceProvider(matchedName, it as List<WebSymbolNameSegment>, namespace, kind, origin,
                              explicitPriority, explicitProximity, dereferencedProperties)
        }

    private fun Map<String, Any>.createPointers(): Map<String, Any> =
      mapValues { (_, value) -> value.createPointers() }

    private fun Any.createPointers(): Any =
      when (this) {
        is Symbol -> createPointer()
        is PsiElement -> createSmartPointer()
        is Map<*, *> -> mapValues { (_, value) -> value?.createPointers() }
        is List<*> -> map { it?.createPointers() }
        is Set<*> -> mapTo(HashSet()) { it?.createPointers() }
        else -> this
      }

    private fun Map<String, Any>.dereferencePointers(anyProblems: Ref<Boolean>): Map<String, Any> =
      mapValues { (_, value) -> value.dereferencePointers(anyProblems) }

    private fun Any.dereferencePointers(anyProblems: Ref<Boolean>): Any =
      when (this) {
        is Pointer<*> -> dereference().also { if (it == null) anyProblems.set(true) } ?: this
        is Map<*, *> -> mapValues { (_, value) -> value?.dereferencePointers(anyProblems) }
        is List<*> -> map { it?.dereferencePointers(anyProblems) }
        is Set<*> -> mapTo(HashSet()) { it?.dereferencePointers(anyProblems) }
        else -> this
      }
  }

}
