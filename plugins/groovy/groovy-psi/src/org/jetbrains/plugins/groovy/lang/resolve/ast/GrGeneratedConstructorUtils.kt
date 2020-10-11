// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrGeneratedConstructorUtils")

package org.jetbrains.plugins.groovy.lang.resolve.ast

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.parentOfType
import groovy.transform.Undefined
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.*
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.hasCodeModifierProperty
import org.jetbrains.plugins.groovy.lang.psi.impl.getArrayValue
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*
import java.util.*

val constructorGeneratingAnnotations: List<String> = listOf(
  GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR,
  GROOVY_TRANSFORM_MAP_CONSTRUCTOR)

object TupleConstructorAttributes {
  @NlsSafe const val EXCLUDES = "excludes"
  @NlsSafe const val INCLUDES = "includes"
  @NlsSafe const val ALL_NAMES = "allNames"
  @NlsSafe const val INCLUDE_PROPERTIES = "includeProperties"
  @NlsSafe const val INCLUDE_FIELDS = "includeFields"
  @NlsSafe const val PRE = "pre"
  @NlsSafe const val POST = "post"
  @NlsSafe const val CALL_SUPER = "callSuper"
  @NlsSafe const val FORCE = "force"
  @NlsSafe const val DEFAULTS = "defaults"
  @NlsSafe const val ALL_PROPERTIES = "allProperties"
  @NlsSafe const val INCLUDE_SUPER_PROPERTIES = "includeSuperProperties"
  @NlsSafe const val INCLUDE_SUPER_FIELDS = "includeSuperFields"
}

fun getIdentifierList(annotation: PsiAnnotation, @NlsSafe attributeName: String): List<String>? {
  annotation.takeIf { it.hasAttribute(attributeName) } ?: return null
  val rawIdentifiers = inferStringAttribute(annotation, attributeName)
  return rawIdentifiers?.split(',')?.mapNotNull { it.trim().takeUnless(CharSequence::isBlank) }?.toList()
         ?: inferStringArrayValueShallow(annotation, attributeName)
}

/**
 * This function prevents evaluation of constant expressions inside attributes,
 * because this evaluation may trigger a recursion within the `TransformationContext`
 */
private fun inferStringArrayValueShallow(anno: PsiAnnotation, attributeName: String): List<String> =
  anno.findAttributeValue(attributeName)?.getArrayValue(GrAnnotationUtil::getString) ?: emptyList()


/**
 * For specific annotation (@MapConstructor/@TupleConstructor) this class computes all actually affected members
 */
data class AffectedMembersCache internal constructor(private val order: List<PsiNamedElement>,
                                                     private val hasPropertyOptions: Boolean,
                                                     private val referencedFromExcludes: List<PsiNamedElement>) {

  companion object {
    @JvmStatic
    fun isInternal(name: String): Boolean = name.contains("$")

    @JvmStatic
    fun getExternalName(namedElement: PsiNamedElement): String? = when (namedElement) {
      is PsiField -> namedElement.name
      is PsiMethod -> PropertyUtilBase.getPropertyName(namedElement)
      else -> null
    }

  }

  /**
   * Returns all members that will be referenced in generated constructor's body
   */
  fun getAffectedMembers(): List<PsiNamedElement> = order

  /**
   * Returns all members that are referenced by annotation, including those ones that are excluded
   */
  fun getAllAffectedMembers(): List<PsiNamedElement> = order + referencedFromExcludes

  fun arePropertiesHandledByUser(): Boolean = hasPropertyOptions
}

fun getAffectedMembersCache(annotation: PsiAnnotation): AffectedMembersCache = CachedValuesManager.getCachedValue(annotation) {
  val cache = computeAffectedMembersCache(annotation)
  val affectedMembers = cache.getAllAffectedMembers()
  CachedValueProvider.Result(cache, annotation, *affectedMembers.toTypedArray())
}

private fun computeAffectedMembersCache(annotation: PsiAnnotation): AffectedMembersCache {
  val owner = annotation.owner as? PsiElement ?: return EmptyAffectedMembersCache
  val containingClass = owner.parentOfType<GrTypeDefinition>()?.takeIf { it.modifierList === owner } ?: return EmptyAffectedMembersCache
  val (nameFilter: (String) -> Boolean, excludedIdentifiers: List<String>, orderFromIncludes: List<String>?) =
    collectNamesOrderInformation(annotation)

  class FilteringList<T>(private val predicate: (T) -> Boolean) : ArrayList<T>() {
    override fun add(element: T): Boolean = if (!predicate(element)) false else super.add(element)
  }

  val resultCollector = FilteringList<PsiNamedElement> { it.name?.takeIf(nameFilter) != null }
  val excludesCollector = FilteringList<PsiNamedElement> { it.name?.takeUnless(nameFilter) in excludedIdentifiers }
  val unifiedCollector = object : ArrayList<PsiNamedElement>() {
    override fun add(element: PsiNamedElement): Boolean {
      resultCollector.add(element)
      excludesCollector.add(element)
      return super.add(element)
    }
  }

  processSuperClasses(containingClass, annotation, unifiedCollector)

  processThisClass(containingClass, annotation, unifiedCollector)

  if (orderFromIncludes != null) {
    val includeComparator = Comparator.comparingInt { param: PsiNamedElement -> orderFromIncludes.indexOf(param.name) }
    resultCollector.sortWith(includeComparator)
  }
  return AffectedMembersCache(resultCollector, containingClass.hasAnnotation(GROOVY_TRANSFORM_PROPERTY_OPTIONS), excludesCollector)
}

val EmptyAffectedMembersCache = AffectedMembersCache(emptyList(), false, emptyList())

private fun processSuperClasses(containingClass: GrTypeDefinition, anno: PsiAnnotation, collector: MutableList<PsiNamedElement>) {
  val acceptSuperProperties = inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_SUPER_PROPERTIES) ?: false
  val acceptSuperFields = inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_SUPER_FIELDS) ?: false
  if (!acceptSuperFields && !acceptSuperProperties) return

  val visited = mutableSetOf<PsiClass>()
  val superClass = containingClass.getSupers(false)[0]
  fun collectSupers(owner: PsiClass?) {
    if (owner == null || !visited.add(owner) || GROOVY_OBJECT_SUPPORT == owner.qualifiedName) return
    collectSupers(owner.superClass)
    acceptClass(owner, acceptSuperProperties, false, acceptSuperFields, false, anno, collector)
  }
  collectSupers(superClass)
}

fun processThisClass(containingClass: PsiClass, anno: PsiAnnotation, collector: MutableList<PsiNamedElement>) {
  val includeFields = inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_FIELDS) ?: true
  val includeProperties = inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_PROPERTIES) ?: true
  val includeBeans = inferBooleanAttribute(anno, TupleConstructorAttributes.ALL_PROPERTIES) ?: false
  val includeStatic = inferBooleanAttribute(anno, "includeStatic") ?: false
  acceptClass(containingClass, includeProperties, includeBeans, includeFields, includeStatic, anno, collector)
}

private fun acceptClass(clazz: PsiClass,
                        includeProperties: Boolean,
                        includeBeans: Boolean,
                        includeFields: Boolean,
                        includeStatic: Boolean,
                        annotation: PsiAnnotation,
                        collector: MutableCollection<PsiNamedElement>) {
  val (properties, setters, fields) = getGroupedClassMembers(clazz)

  fun addParameter(origin: PsiField) {
    if (!includeStatic && hasCodeModifierProperty(origin, PsiModifier.STATIC)) return
    collector.add(origin)
  }

  if (includeProperties) {
    for (property in properties) {
      addParameter(property)
    }
  }

  if (includeBeans) {
    for (method in setters) {
      if (!includeStatic && hasCodeModifierProperty(method, PsiModifier.STATIC)) continue
      collector.add(method)
    }
  }

  if (includeFields) {
    for (field in fields) {
      if (annotation.hasQualifiedName(GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR) && hasCodeModifierProperty(field, GrModifier.FINAL) && field.initializer != null) continue
      addParameter(field)
    }
  }
}

private fun collectNamesOrderInformation(tupleConstructor: PsiAnnotation): Triple<(String) -> Boolean, List<String>, List<String>?> {

  val excludes: List<String> = getIdentifierList(tupleConstructor, TupleConstructorAttributes.EXCLUDES) ?: emptyList()

  val includes: List<String>? = getIdentifierList(tupleConstructor, TupleConstructorAttributes.INCLUDES)
    ?.takeUnless { Undefined.isUndefined(it.singleOrNull()) }

  val allowInternalNames = inferBooleanAttribute(tupleConstructor, TupleConstructorAttributes.ALL_NAMES) ?: false

  val filter: (String) -> Boolean = { name: String ->
    val internalFilter = allowInternalNames || !AffectedMembersCache.isInternal(name)
    val excludesFilter = !excludes.contains(name)
    val includesFilter = includes == null || includes.contains(name)
    internalFilter && excludesFilter && includesFilter
  }
  return Triple(filter, excludes, includes)
}

fun getGroupedClassMembers(psiClass: PsiClass): Triple<List<PsiField>, List<PsiMethod>, List<PsiField>> {
  val fields: MutableList<PsiField> = mutableListOf()
  val properties: MutableList<PsiField> = mutableListOf()
  val allFields = if (psiClass is GrTypeDefinition) psiClass.codeFields else psiClass.fields
  for (field: PsiField in allFields) {
    if (field is GrField && field.isProperty) properties.add(field) else fields.add(field)
  }

  val methods: Array<out PsiMethod> = if (psiClass is GrTypeDefinition) psiClass.codeMethods else psiClass.methods
  val propertySetters = methods.filterIsInstance<GrMethod>().filter {
    hasCodeModifierProperty(it, PsiModifier.PUBLIC) &&
    !hasCodeModifierProperty(it, PsiModifier.STATIC) &&
    PropertyUtilBase.isSimplePropertySetter(it)
  }.map { PropertyUtilBase.getPropertyName(it) to it }.toMap()
  val fieldNames = allFields.map(PsiField::getName)
  val setters: List<PsiMethod> = propertySetters.filterKeys { !fieldNames.contains(it) }.values.toList()

  return Triple(properties, setters, fields)
}