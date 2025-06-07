// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.*
import com.intellij.polySymbols.PolySymbol.Priority
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationTargetImpl
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolMatchBuilder
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.utils.coalesceApiStatus
import com.intellij.polySymbols.utils.merge
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import javax.swing.Icon

internal open class PolySymbolMatchBase internal constructor(
  override val matchedName: String,
  override val nameSegments: List<PolySymbolNameSegment>,
  override val qualifiedKind: PolySymbolQualifiedKind,
  override val origin: PolySymbolOrigin,
  override val explicitPriority: Priority?,
  override val explicitProximity: Int?,
  override val additionalProperties: Map<String, Any>,
) : PolySymbolMatchMixin {

  init {
    require(nameSegments.isNotEmpty()) { "nameSegments must not be empty" }
  }

  internal fun withSegments(segments: List<PolySymbolNameSegment>): PolySymbolMatch =
    create(matchedName, segments, qualifiedKind, origin, explicitPriority, explicitProximity, additionalProperties)


  override fun equals(other: Any?): Boolean =
    other is PolySymbolMatch
    && other.name == name
    && other.origin == origin
    && other.qualifiedKind == qualifiedKind
    && other.nameSegments.equalsIgnoreOffset(nameSegments)

  override fun hashCode(): Int = name.hashCode()

  override fun createPointer(): Pointer<out PolySymbolMatchBase> =
    PolySymbolMatchPointer<PolySymbolMatchBase>(this, ::PolySymbolMatchBase)

  class BuilderImpl(
    private var matchedName: String,
    private var qualifiedKind: PolySymbolQualifiedKind,
    private var origin: PolySymbolOrigin,
  ) : PolySymbolMatchBuilder {

    private var nameSegments = mutableListOf<PolySymbolNameSegment>()
    private var properties = mutableMapOf<String, Any>()
    private var explicitPriority: Priority? = null
    private var explicitProximity: Int? = null

    fun build(): PolySymbolMatch =
      create(matchedName, nameSegments, qualifiedKind,
             origin, explicitPriority, explicitProximity, properties)

    override fun addNameSegments(value: List<PolySymbolNameSegment>): PolySymbolMatchBuilder = this.also {
      nameSegments.addAll(value)
    }

    override fun addNameSegments(vararg value: PolySymbolNameSegment): PolySymbolMatchBuilder = this.also {
      nameSegments.addAll(value)
    }

    override fun addNameSegment(value: PolySymbolNameSegment): PolySymbolMatchBuilder = this.also {
      nameSegments.add(value)
    }

    override fun explicitPriority(value: Priority): PolySymbolMatchBuilder = this.also {
      explicitPriority = value
    }

    override fun explicitProximity(value: Int): PolySymbolMatchBuilder = this.also {
      explicitProximity = value
    }

    override fun setProperty(name: String, value: Any): PolySymbolMatchBuilder = this.also {
      properties[name] = value
    }
  }

}

private class PsiSourcedPolySymbolMatch(
  matchedName: String,
  nameSegments: List<PolySymbolNameSegment>,
  qualifiedKind: PolySymbolQualifiedKind,
  origin: PolySymbolOrigin,
  explicitPriority: Priority?,
  explicitProximity: Int?,
  additionalProperties: Map<String, Any>,
) : PolySymbolMatchBase(matchedName, nameSegments, qualifiedKind, origin, explicitPriority, explicitProximity, additionalProperties),
    PsiSourcedPolySymbolMatchMixin {

  override fun createPointer(): Pointer<PsiSourcedPolySymbolMatch> =
    PolySymbolMatchPointer<PsiSourcedPolySymbolMatch>(this, ::PsiSourcedPolySymbolMatch)

}

private class PsiSourcedPolySymbolMatchWithDocumentation(
  matchedName: String,
  nameSegments: List<PolySymbolNameSegment>,
  qualifiedKind: PolySymbolQualifiedKind,
  origin: PolySymbolOrigin,
  explicitPriority: Priority?,
  explicitProximity: Int?,
  additionalProperties: Map<String, Any>,
) : PolySymbolMatchBase(matchedName, nameSegments, qualifiedKind, origin, explicitPriority, explicitProximity, additionalProperties),
    PsiSourcedPolySymbolMatchMixin, PolySymbolMatchWithDocumentationMixin {

  override fun createPointer(): Pointer<PsiSourcedPolySymbolMatchWithDocumentation> =
    PolySymbolMatchPointer<PsiSourcedPolySymbolMatchWithDocumentation>(this, ::PsiSourcedPolySymbolMatchWithDocumentation)

}

private class PolySymbolMatchWithDocumentation(
  matchedName: String,
  nameSegments: List<PolySymbolNameSegment>,
  qualifiedKind: PolySymbolQualifiedKind,
  origin: PolySymbolOrigin,
  explicitPriority: Priority?,
  explicitProximity: Int?,
  additionalProperties: Map<String, Any>,
) : PolySymbolMatchBase(matchedName, nameSegments, qualifiedKind, origin, explicitPriority, explicitProximity, additionalProperties),
    PolySymbolMatchWithDocumentationMixin {

  override fun createPointer(): Pointer<PolySymbolMatchWithDocumentation> =
    PolySymbolMatchPointer<PolySymbolMatchWithDocumentation>(this, ::PolySymbolMatchWithDocumentation)

}

private fun create(
  matchedName: String,
  nameSegments: List<PolySymbolNameSegment>,
  qualifiedKind: PolySymbolQualifiedKind,
  origin: PolySymbolOrigin,
  explicitPriority: Priority?,
  explicitProximity: Int?,
  additionalProperties: Map<String, Any>,
): PolySymbolMatch {
  val psiSourcedMixin = nameSegments.all { it.start == it.end || (it.symbols.isNotEmpty() && it.symbols.any { symbol -> symbol is PsiSourcedPolySymbol }) }
  val withDocumentationMixin = nameSegments.any { it.symbols.any { symbol -> symbol is PolySymbolWithDocumentation } }

  return if (psiSourcedMixin) {
    if (withDocumentationMixin) {
      PsiSourcedPolySymbolMatchWithDocumentation(matchedName, nameSegments, qualifiedKind, origin,
                                                 explicitPriority, explicitProximity, additionalProperties)
    }
    else {
      PsiSourcedPolySymbolMatch(matchedName, nameSegments, qualifiedKind, origin,
                                explicitPriority, explicitProximity, additionalProperties)
    }
  }
  else {
    if (withDocumentationMixin) {
      PolySymbolMatchWithDocumentation(matchedName, nameSegments, qualifiedKind, origin,
                                       explicitPriority, explicitProximity, additionalProperties)
    }
    else {
      PolySymbolMatchBase(matchedName, nameSegments, qualifiedKind, origin,
                          explicitPriority, explicitProximity, additionalProperties)
    }
  }
}

private interface PolySymbolMatchMixin : PolySymbolMatch {

  val explicitPriority: Priority?
  val explicitProximity: Int?
  val additionalProperties: Map<String, Any>

  fun reversedSegments() = Sequence { ReverseListIterator(nameSegments) }

  override fun withCustomProperties(properties: Map<String, Any>): PolySymbolMatch =
    create(matchedName, nameSegments, qualifiedKind, origin, explicitPriority, explicitProximity, additionalProperties + properties)

  override val psiContext: PsiElement?
    get() = reversedSegments().flatMap { it.symbols.asSequence() }
      .mapNotNull { it.psiContext }.firstOrNull()


  override val name: String
    get() = matchedName.substring(nameSegments.firstOrNull()?.start ?: 0,
                                  nameSegments.lastOrNull()?.end ?: 0)

  override val virtual: Boolean
    get() = nameSegments.any { segment -> segment.symbols.any { it.virtual } }

  override val extension: Boolean
    get() = nameSegments.isNotEmpty() && nameSegments.all { segment -> segment.symbols.isNotEmpty() && segment.symbols.all { it.extension } }

  override val priority: Priority?
    get() = explicitPriority ?: reversedSegments().mapNotNull { it.priority }.firstOrNull()

  override val queryScope: List<PolySymbolsScope>
    get() = nameSegments.asSequence()
      .flatMap { it.symbols }
      .flatMap { it.queryScope }
      .toList()

  override val type: Any?
    get() = reversedSegments().flatMap { it.symbols }
      .mapNotNull { it.type }.firstOrNull()

  override val attributeValue: PolySymbolHtmlAttributeValue?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.attributeValue }.merge()

  override val required: Boolean?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.required }.firstOrNull()

  override val apiStatus: PolySymbolApiStatus
    get() = coalesceApiStatus(reversedSegments().flatMap { it.symbols }) { it.apiStatus }

  override val icon: Icon?
    get() = reversedSegments().flatMap { it.symbols }.mapNotNull { it.icon }.firstOrNull()

  override val properties: Map<String, Any>
    get() = nameSegments.asSequence().flatMap { it.symbols }
      .flatMap { it.properties.entries }
      .filter { it.key != PolySymbol.PROP_HIDE_FROM_COMPLETION }
      .plus(additionalProperties.entries)
      .map { Pair(it.key, it.value) }
      .toMap()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (nameSegments.size == 1)
      nameSegments[0].symbols.asSequence()
        .flatMap { it.getNavigationTargets(project) }
        .distinct()
        .toList()
    else emptyList()

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    reversedSegments()
      .flatMap { it.symbols.asSequence() }
      .map {
        if (it === this) null
        else it.getDocumentationTarget(location)
      }
      .filter { it !is PolySymbolDocumentationTargetImpl || it.symbol.createDocumentation(location)?.isNotEmpty() == true }
      .firstOrNull()
    ?: if (this is PolySymbolWithDocumentation) PolySymbolDocumentationTargetImpl(this, location) else null

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    super<PolySymbolMatch>.isEquivalentTo(symbol)
    || nameSegments.filter { it.start != it.end }
      .let { nonEmptySegments ->
        nonEmptySegments.size == 1
        && nonEmptySegments[0].symbols.any { it.isEquivalentTo(symbol) }
      }

  override val searchTarget: PolySymbolSearchTarget?
    get() = nameSegments.filter { it.start != it.end }
      .takeIf { it.size == 1 }
      ?.get(0)
      ?.symbols
      ?.takeIf { it.size == 1 }
      ?.get(0)
      ?.searchTarget

  override val renameTarget: PolySymbolRenameTarget?
    get() = nameSegments.filter { it.start != it.end }
      .takeIf { it.size == 1 }
      ?.get(0)
      ?.symbols
      ?.takeIf { it.size == 1 }
      ?.get(0)
      ?.renameTarget

}


private interface PolySymbolMatchWithDocumentationMixin : PolySymbolMatchMixin, PolySymbolWithDocumentation {

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    super<PolySymbolMatchMixin>.getDocumentationTarget(location)

  override fun createDocumentation(location: PsiElement?): PolySymbolDocumentation? =
    reversedSegments().flatMap { it.symbols.asSequence() }
      .firstNotNullOfOrNull { (it as? PolySymbolWithDocumentation)?.createDocumentation(location) }

  override val description: String?
    get() = nameSegments.takeIf { it.size == 1 }
      ?.get(0)?.symbols?.asSequence()?.mapNotNull { (it as? PolySymbolWithDocumentation)?.description }?.firstOrNull()

  override val docUrl: String?
    get() = nameSegments.takeIf { it.size == 1 }
      ?.get(0)?.symbols?.asSequence()?.mapNotNull { (it as? PolySymbolWithDocumentation)?.docUrl }?.firstOrNull()

  override val descriptionSections: Map<String, String>
    get() = nameSegments.takeIf { it.size == 1 }
              ?.get(0)?.symbols?.asSequence()
              ?.flatMap { (it as? PolySymbolWithDocumentation)?.descriptionSections?.asSequence() ?: emptySequence() }
              ?.distinct()
              ?.associateBy({ it.key }, { it.value })
            ?: emptyMap()

}

private interface PsiSourcedPolySymbolMatchMixin : PolySymbolMatchMixin, PsiSourcedPolySymbol {

  override val psiContext: PsiElement?
    get() = reversedSegments().flatMap { it.symbols.asSequence() }
      .mapNotNull { it.psiContext }.firstOrNull()

  override val source: PsiElement?
    get() = reversedSegments().flatMap { it.symbols }
      .mapNotNull { (it as? PsiSourcedPolySymbol)?.source }.singleOrNull()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    super<PolySymbolMatchMixin>.getNavigationTargets(project)

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    super<PolySymbolMatchMixin>.isEquivalentTo(symbol)

}

private fun List<PolySymbolNameSegment>.equalsIgnoreOffset(other: List<PolySymbolNameSegment>): Boolean {
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
    ) {
      return false
    }
  }
  return true
}

private class ReverseListIterator<T>(list: List<T>) : Iterator<T> {

  private val iterator = list.listIterator(list.size)

  override operator fun hasNext(): Boolean {
    return iterator.hasPrevious()
  }

  override operator fun next(): T {
    return iterator.previous()
  }

}

private class PolySymbolMatchPointer<T : PolySymbolMatch>(
  polySymbolMatch: PolySymbolMatchBase,
  private val newInstanceProvider: (
    matchedName: String,
    nameSegments: List<PolySymbolNameSegment>,
    qualifiedKind: PolySymbolQualifiedKind,
    origin: PolySymbolOrigin,
    explicitPriority: Priority?,
    explicitProximity: Int?,
    additionalProperties: Map<String, Any>,
  ) -> T,
) : Pointer<T> {

  private val matchedName = polySymbolMatch.matchedName
  private val nameSegments = polySymbolMatch.nameSegments
    .map { it.createPointer() }
  private val qualifiedKind = polySymbolMatch.qualifiedKind
  private val origin = polySymbolMatch.origin
  private val explicitPriority = polySymbolMatch.explicitPriority
  private val explicitProximity = polySymbolMatch.explicitProximity
  private val additionalProperties = polySymbolMatch.additionalProperties
    .createPointers()

  override fun dereference(): T? =
    nameSegments.map { it.dereference() }
      .takeIf { it.all { segment -> segment != null } }
      ?.let {
        val dereferencingProblems = Ref(false)
        val dereferencedProperties = additionalProperties.dereferencePointers(dereferencingProblems)
        if (dereferencingProblems.get()) return null

        @Suppress("UNCHECKED_CAST")
        newInstanceProvider(matchedName, it as List<PolySymbolNameSegment>, qualifiedKind, origin,
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
