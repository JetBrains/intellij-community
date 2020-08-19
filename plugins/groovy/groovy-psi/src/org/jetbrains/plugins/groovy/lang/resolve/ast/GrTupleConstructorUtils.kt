// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrTupleConstructorUtils")

package org.jetbrains.plugins.groovy.lang.resolve.ast

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiAnnotation
import groovy.transform.Undefined
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames


interface TupleConstructorAttributes {
  companion object {
    @NlsSafe
    const val EXCLUDES = "excludes"
    @NlsSafe
    const val INCLUDES = "includes"
    @NlsSafe
    const val ALL_NAMES = "allNames"
    @NlsSafe
    const val INCLUDE_PROPERTIES = "includeProperties"
    @NlsSafe
    const val INCLUDE_FIELDS = "includeFields"
    @NlsSafe
    const val PRE = "pre"
    @NlsSafe
    const val POST = "post"
    @NlsSafe
    const val CALL_SUPER = "callSuper"
  }
}

fun getIdentifierList(annotation: PsiAnnotation, @NlsSafe attributeName: String): List<String>? {
  annotation.takeIf { it.hasAttribute(attributeName) } ?: return null
  val rawIdentifiers = GrAnnotationUtil.inferStringAttribute(annotation, attributeName)
  return rawIdentifiers?.split(',')?.mapNotNull { it.trim().takeUnless(CharSequence::isBlank) }?.toList()
         ?: GrAnnotationUtil.getStringArrayValue(annotation, attributeName, false)
}


private fun String.isInternal(): Boolean = contains("$")

internal fun collectNamesOrderInformation(tupleConstructor: PsiAnnotation): Pair<(String) -> Boolean, List<String>?> {

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
  return filter to includes
}


fun isFieldAccepted(annotation: PsiAnnotation, field: GrField): Boolean {
  if (field.hasModifierProperty(GrModifier.STATIC)) return false
  if (field.isProperty) {
    val hasCustomPropertyHandler = field.containingClass?.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS) ?: false
    if (hasCustomPropertyHandler) return false
    val includeProperties = GrAnnotationUtil.inferBooleanAttribute(annotation, TupleConstructorAttributes.INCLUDE_PROPERTIES) ?: true
    if (!includeProperties) return false
  }
  else {
    val includeFields = GrAnnotationUtil.inferBooleanAttribute(annotation, TupleConstructorAttributes.INCLUDE_FIELDS) ?: false
    if (!includeFields) return false
  }
  val (namesFilter, _) = collectNamesOrderInformation(annotation)
  return namesFilter(field.name)
}

