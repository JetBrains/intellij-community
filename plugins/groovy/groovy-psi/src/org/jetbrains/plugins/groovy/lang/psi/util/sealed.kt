// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SealedUtil")
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.SealedHelper.inferReferencedClass

internal fun getAllSealedElements(typeDef : GrTypeDefinition) : List<PsiElement> {
  val modifier = typeDef.modifierList?.getModifier(GrModifier.SEALED)
  val annotation = typeDef.modifierList?.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED)
  return listOf(modifier, annotation).filterNotNullTo(SmartList())
}

fun GrTypeDefinition.getSealedElement() : PsiElement? = getAllSealedElements(this).firstOrNull()

fun getAllPermittedClassElements(typeDef : GrTypeDefinition) : List<PsiElement> {
  var isTypeDefSealed = false
  if (typeDef.hasModifierProperty(GrModifier.SEALED)) {
    isTypeDefSealed = true
    if (typeDef.permitsClause?.keyword != null) {
      return typeDef.permitsClause?.referenceElementsGroovy?.toList() ?: emptyList()
    }
  }
  else {
    val annotation = typeDef.modifierList?.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED) as? GrAnnotation
    val declaredAttribute = annotation?.findDeclaredAttributeValue("permittedSubclasses")
    if (annotation != null) {
      isTypeDefSealed = true
      if (declaredAttribute != null) {
        return AnnotationUtil.arrayAttributeValues(declaredAttribute)
      }
    }
  }
  if (isTypeDefSealed) {
    return (typeDef.containingFile as? GroovyFile)?.classes?.filter { typeDef in (it.extendsListTypes + it.implementsListTypes).mapNotNull(PsiClassType::resolve) }
           ?: emptyList()
  }
  return emptyList()
}

// reduce visibility of this function to avoid misuse
object SealedHelper {
  fun inferReferencedClass(element : PsiElement) : PsiClass? = when (element) {
    is GrCodeReferenceElement -> element.resolve() as? PsiClass
    is PsiAnnotationMemberValue -> GrAnnotationUtil.getPsiClass(element)
    is PsiClass -> element
    else -> null
  }
}

fun getAllPermittedClasses(typeDef: GrTypeDefinition): List<PsiClass> =
  getAllPermittedClassElements(typeDef).mapNotNull(::inferReferencedClass)