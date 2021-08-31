// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("SealedUtil")
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil

internal fun getAllSealedElements(typeDef : GrTypeDefinition) : List<PsiElement> {
  val modifier = typeDef.modifierList?.getModifier(GrModifier.SEALED)
  val annotation = typeDef.modifierList?.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED)
  return listOf(modifier, annotation).filterNotNullTo(SmartList())
}

fun GrTypeDefinition.getSealedElement() : PsiElement? = getAllSealedElements(this).firstOrNull()

fun getAllPermittedClassElements(typeDef : GrTypeDefinition) : List<PsiElement> {
  val permitsClause = typeDef.permitsClause?.referenceElementsGroovy ?: emptyArray()
  val annotation = (typeDef.modifierList?.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED) as? GrAnnotation)
  val value = if (annotation != null) AnnotationUtil.arrayAttributeValues(annotation.findDeclaredAttributeValue ("permittedSubclasses")) else emptyList()
  return permitsClause.toList() + value
}

fun getAllPermittedClassesWithElements(typeDef : GrTypeDefinition) : List<PsiClass> {
  val permitsClause = typeDef.permitsClause?.referencedTypes?.mapNotNull { it.resolve() } ?: emptyList()
  val annotation = (typeDef.modifierList?.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED) as? GrAnnotation)
  val value = if (annotation != null) GrAnnotationUtil.getClassArrayValue(annotation, "permittedSubclasses", true) else emptyList()
  return permitsClause + value
}