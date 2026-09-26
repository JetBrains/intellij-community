// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.uast

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationEx
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParameterEx
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastFacade

class GrUClass(private val grElement: GrTypeDefinition,
               parentProvider: () -> UElement?) : UClass, UAnchorOwner, UDeclarationEx, PsiClass by grElement {

  override val sourcePsi: GrTypeDefinition = grElement

  override val javaPsi: PsiClass = grElement

  override val psi: GrTypeDefinition = sourcePsi

  override fun getQualifiedName(): String? = grElement.qualifiedName

  override val uastSuperTypes: List<UTypeReferenceExpression> = emptyList() //not implemented

  override val uastDeclarations: MutableList<UDeclaration> by lazy {
    mutableListOf<UDeclaration>().apply {
      addAll(fields)
      addAll(initializers)
      addAll(methods)
      addAll(innerClasses)
    }
  }

  override val uastAnchor: UIdentifier
    get() = UIdentifier(grElement.nameIdentifierGroovy, this)

  override val uastParent: UElement? by lazy(parentProvider)

  override val uAnnotations: List<UAnnotation> by lazy { grAnnotations(grElement.modifierList, this) }

  override fun getSuperClass(): UClass? = super.getSuperClass()

  override fun getFields(): Array<UField> = emptyArray() //not implemented

  override fun getInitializers(): Array<UClassInitializer> = emptyArray() //not implemented

  override fun getMethods(): Array<UMethod> = grElement.codeMethods.map { GrUMethod(it, { this }) }.toTypedArray()

  override fun getInnerClasses(): Array<UClass> = grElement.codeInnerClasses.map { GrUClass(it, { this }) }.toTypedArray()

  override fun getOriginalElement(): PsiElement = grElement.originalElement
}


class GrUMethod(private val grElement: GrMethod,
                parentProvider: () -> UElement?) : UMethod, UAnchorOwner, UDeclarationEx, PsiMethod by grElement {
  override val uastParent: UElement? by lazy(parentProvider)

  override val sourcePsi: PsiElement = grElement
  override val javaPsi: PsiMethod = grElement

  override val psi: PsiMethod = javaPsi

  override val uastBody: UExpression? = null //not implemented

  override val uastParameters: List<UParameter> by lazy { grElement.parameters.map { GrUParameter(it, { this }) } }

  override val uastAnchor: UIdentifier
    get() = UIdentifier(grElement.nameIdentifierGroovy, this)

  override val uAnnotations: List<UAnnotation> by lazy { grAnnotations(grElement.modifierList, this) }

  override fun getBody(): PsiCodeBlock? = null

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement

}

class GrUParameter(private val grElement: GrParameter,
                   parentProvider: () -> UElement?) : UParameterEx, UAnchorOwner, PsiParameter by grElement {
  override val uastParent: UElement? by lazy(parentProvider)

  override val sourcePsi: PsiElement = grElement
  override val javaPsi: PsiParameter = grElement

  override val psi: PsiParameter = javaPsi

  override val uastInitializer: UExpression? by lazy {
    val initializer = grElement.initializerGroovy ?: return@lazy null
    UastFacade.findPlugin(initializer)?.convertElement(initializer, this) as? UExpression
  }

  override val typeReference: UTypeReferenceExpression? = null //not implemented

  override val uastAnchor: UIdentifier
    get() = UIdentifier(grElement.nameIdentifierGroovy, this)

  override val uAnnotations: List<UAnnotation> by lazy { grAnnotations(grElement.modifierList, this) }

  override fun getInitializer(): PsiExpression? = grElement.initializer

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}

private fun grAnnotations(modifierList: GrModifierList?, parent: UElement): List<GrUAnnotation> =
  modifierList?.rawAnnotations?.map { GrUAnnotation(it, { parent }) } ?: emptyList()

