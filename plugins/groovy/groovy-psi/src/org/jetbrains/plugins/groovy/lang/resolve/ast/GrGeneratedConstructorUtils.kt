// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrGeneratedConstructorUtils")

package org.jetbrains.plugins.groovy.lang.resolve.ast

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.parentOfType
import groovy.transform.Undefined
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import java.util.*
import kotlin.collections.ArrayList

val constructorGeneratingAnnotations: List<String> = listOf(
  GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR,
  GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR)

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
  val rawIdentifiers = GrAnnotationUtil.inferStringAttribute(annotation, attributeName)
  return rawIdentifiers?.split(',')?.mapNotNull { it.trim().takeUnless(CharSequence::isBlank) }?.toList()
         ?: GrAnnotationUtil.getStringArrayValue(annotation, attributeName, false)
}

/**
 * For specific annotation (@MapConstructor/@TupleConstructor) this class computes all actually affected members, and allows to check
 * whether arbitrary identifier is handled by the annotation
 */
class AffectedMembersCache(anno: PsiAnnotation) {
  private val order: List<PsiNamedElement>
  private val hasPropertyOptions: Boolean
  private val referencedFromExcludes: List<PsiNamedElement>

  private class FilteringList<T>(private val predicate: (T) -> Boolean) : ArrayList<T>() {
    override fun add(element: T): Boolean {
      if (!predicate(element)) return false
      return super.add(element)
    }
  }

  init {
    val owner = (anno.owner as? PsiElement)
    val containingClass = owner?.parentOfType<GrTypeDefinition>()?.takeIf { it.modifierList === owner }
    if (containingClass != null) {
      val (nameFilter, excludesIdentifiers, ordering) = collectNamesOrderInformation(anno)
      val acceptFields = GrAnnotationUtil.inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_FIELDS) ?: true
      val acceptProperties = GrAnnotationUtil.inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_PROPERTIES) ?: true
      val includeBeans = GrAnnotationUtil.inferBooleanAttribute(anno, TupleConstructorAttributes.ALL_PROPERTIES) ?: false
      val acceptSuperProperties = GrAnnotationUtil.inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_SUPER_PROPERTIES) ?: false
      val acceptSuperFields = GrAnnotationUtil.inferBooleanAttribute(anno, TupleConstructorAttributes.INCLUDE_SUPER_FIELDS) ?: false
      val includeStatic = GrAnnotationUtil.inferBooleanAttribute(anno, "includeStatic") ?: false
      val collector = FilteringList<PsiNamedElement> { it.name?.takeIf(nameFilter) != null }
      val excludesCollector = FilteringList<PsiNamedElement> { it.name?.takeUnless(nameFilter) in excludesIdentifiers }
      if (acceptSuperFields || acceptSuperProperties) {
        val visited = mutableSetOf<PsiClass>()
        val superClass = containingClass.getSupers(false)[0]
        collectSupers(superClass, acceptSuperFields, acceptSuperProperties, collector, excludesCollector, visited)
      }
      referencedFromExcludes = excludesCollector
      accept(containingClass, acceptProperties, includeBeans, acceptFields, includeStatic, excludesCollector, collector)
      if (ordering != null) {
        val includeComparator = Comparator.comparingInt { param: PsiNamedElement -> ordering.indexOf(param.name) }
        collector.sortWith(includeComparator)
      }
      order = collector
      hasPropertyOptions = containingClass.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS)
    }
    else {
      order = emptyList()
      hasPropertyOptions = false
      referencedFromExcludes = emptyList()
    }

  }

  private fun collectSupers(owner: PsiClass?,
                            acceptSuperFields: Boolean,
                            acceptSuperProperties: Boolean,
                            collector: MutableList<PsiNamedElement>,
                            excludesCollector: MutableList<PsiNamedElement>,
                            visited: MutableCollection<PsiClass>) {
    if (owner == null || !visited.add(owner) || GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT == owner.qualifiedName) {
      return
    }
    collectSupers(owner.superClass, acceptSuperFields, acceptSuperProperties, collector, excludesCollector, visited)
    accept(owner, acceptSuperProperties, false, acceptSuperFields, false, excludesCollector, collector)
  }

  companion object {

    fun accept(clazz: PsiClass,
               includeProperties: Boolean,
               includeBeans: Boolean,
               includeFields: Boolean,
               includeStatic: Boolean,
               excludesCollector: MutableList<PsiNamedElement>,
               collector: MutableCollection<PsiNamedElement>) {
      val (properties, setters, fields) = getGroupedClassMembers(clazz)

      fun addParameter(origin: PsiField) {
        if (!includeStatic && origin.hasModifierProperty(PsiModifier.STATIC)) return
        excludesCollector.add(origin)
        collector.add(origin)
      }

      if (includeProperties) {
        for (property in properties) {
          addParameter(property)
        }
      }

      if (includeBeans) {
        for (method in setters) {
          if (!includeStatic && method.hasModifierProperty(PsiModifier.STATIC)) continue
          excludesCollector.add(method)
          collector.add(method)
        }
      }

      if (includeFields) {
        for (field in fields) {
          if (field.hasModifierProperty(GrModifier.FINAL) && field.initializer != null) continue
          addParameter(field)
        }
      }
    }

    private fun collectNamesOrderInformation(tupleConstructor: PsiAnnotation): Triple<(String) -> Boolean, List<String>, List<String>?> {

      val excludes: List<String> = getIdentifierList(tupleConstructor, TupleConstructorAttributes.EXCLUDES) ?: emptyList()

      val includes: List<String>? = getIdentifierList(tupleConstructor, TupleConstructorAttributes.INCLUDES)
        ?.takeUnless { Undefined.isUndefined(it.singleOrNull()) }

      val allowInternalNames = GrAnnotationUtil.inferBooleanAttribute(tupleConstructor, TupleConstructorAttributes.ALL_NAMES) ?: false

      val filter: (String) -> Boolean = { name: String ->
        val internalFilter = allowInternalNames || !name.isInternal()
        val excludesFilter = !excludes.contains(name)
        val includesFilter = includes == null || includes.contains(name)
        internalFilter && excludesFilter && includesFilter
      }
      return Triple(filter, excludes, includes)
    }

    private fun getGroupedClassMembers(psiClass: PsiClass): Triple<List<PsiField>, List<PsiMethod>, List<PsiField>> {
      val fields: MutableList<PsiField> = ArrayList()
      val properties: MutableList<PsiField> = ArrayList()
      val allFields = if (psiClass is GrTypeDefinition) psiClass.codeFields else psiClass.fields
      for (field: PsiField in allFields) {
        if (field is GrField && field.isProperty) properties.add(field) else fields.add(field)
      }

      val methods: Array<out PsiMethod> = if (psiClass is GrTypeDefinition) psiClass.codeMethods else psiClass.methods
      val propertySetters = PropertyUtilBase.getAllProperties(true, false, methods)
      val fieldNames = allFields.map(PsiField::getName)
      val setters: List<PsiMethod> = propertySetters.filterKeys { !fieldNames.contains(it) }.values.toList()

      return Triple(properties, setters, fields)
    }

    private fun String.isInternal(): Boolean = contains("$")

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