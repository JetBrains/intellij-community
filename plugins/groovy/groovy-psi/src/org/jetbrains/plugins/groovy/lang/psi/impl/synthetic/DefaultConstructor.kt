// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic

import com.intellij.psi.*
import com.intellij.psi.impl.PsiSuperMethodImplUtil.getHierarchicalMethodSignature
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameter
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import com.intellij.util.IncorrectOperationException
import kotlin.LazyThreadSafetyMode.PUBLICATION

class DefaultConstructor(
  private val myConstructedClass: PsiClass
) : LightElement(myConstructedClass.manager, myConstructedClass.language), PsiMethod {

  override fun getNavigationElement(): PsiElement = myConstructedClass

  override fun getName() = myConstructedClass.name!!

  override fun getNameIdentifier(): PsiIdentifier? = null

  override fun hasModifierProperty(name: String): Boolean = false

  private val myModifierList by lazy(PUBLICATION) { LightModifierList(manager, language) }

  override fun getModifierList(): PsiModifierList = myModifierList

  private val myParameterList by lazy(PUBLICATION) {
    LightParameterListBuilder(manager, language).apply {
      if (myConstructedClass.hasModifierProperty(PsiModifier.STATIC)) return@apply
      val enclosingClass = myConstructedClass.containingClass ?: return@apply
      // at this point myConstructedClass is an inner class
      // and it will have single parameter of enclosing class type
      val factory = JVMElementFactories.requireFactory(language, project)
      val type = factory.createType(enclosingClass)
      val parameter = LightParameter("enclosing", type, this@DefaultConstructor)
      addParameter(parameter)
    }
  }

  override fun getParameterList(): PsiParameterList = myParameterList

  override fun getContainingClass(): PsiClass? = myConstructedClass

  override fun getReturnType(): PsiType? = null

  override fun getReturnTypeElement(): PsiTypeElement? = null

  override fun getTypeParameterList(): PsiTypeParameterList? = null

  override fun getTypeParameters(): Array<PsiTypeParameter> = PsiTypeParameter.EMPTY_ARRAY

  private val myThrowsList by lazy(PUBLICATION) { LightReferenceList(manager) }

  override fun getThrowsList(): PsiReferenceList = myThrowsList

  override fun hasTypeParameters(): Boolean = false

  override fun getBody(): PsiCodeBlock? = null

  override fun isConstructor(): Boolean = true

  override fun isVarArgs(): Boolean = false

  override fun getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  override fun getHierarchicalMethodSignature() = getHierarchicalMethodSignature(this)

  override fun isDeprecated(): Boolean = myConstructedClass.isDeprecated

  override fun toString() = "Default constructor for ${myConstructedClass.name}"

  // --

  override fun findSuperMethods(): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun findSuperMethods(checkAccess: Boolean): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun findSuperMethods(parentClass: PsiClass?): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) = emptyList<MethodSignatureBackedByPsiMethod>()

  override fun findDeepestSuperMethods(): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun getDocComment(): PsiDocComment? = null

  @Suppress("OverridingDeprecatedMember")
  override fun findDeepestSuperMethod(): PsiMethod? = null

  override fun setName(name: String): PsiElement = throw IncorrectOperationException("setName() isn't supported")
}