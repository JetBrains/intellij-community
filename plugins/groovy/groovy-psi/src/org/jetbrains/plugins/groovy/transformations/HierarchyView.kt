// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.psi.*
import com.intellij.psi.impl.InheritanceImplUtil
import com.intellij.psi.impl.light.AbstractLightClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ArrayUtil.mergeArrays
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.hasCodeModifierProperty
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil

internal class HierarchyView(
  private val myCodeClass: GrTypeDefinition,
  private val myExtendsTypes: Array<out PsiClassType>,
  private val myImplementsTypes: Array<out PsiClassType>,
  manager: PsiManager
) : AbstractLightClass(manager, GroovyLanguage) {

  override fun isValid(): Boolean = myCodeClass.isValid
  override fun getContainingFile(): PsiFile = myCodeClass.containingFile
  override fun getResolveScope(): GlobalSearchScope = myCodeClass.resolveScope
  override fun isEquivalentTo(another: PsiElement?): Boolean = this === another
  override fun getContext(): PsiElement? = myCodeClass.context

  override fun getName(): String? = myCodeClass.name
  override fun getQualifiedName(): String? = myCodeClass.qualifiedName
  override fun getContainingClass(): PsiClass? = myCodeClass.containingClass
  override fun isInterface(): Boolean = myCodeClass.isInterface
  override fun isEnum(): Boolean = myCodeClass.isEnum
  override fun getModifierList(): PsiModifierList? = myCodeClass.modifierList
  override fun hasModifierProperty(name: String): Boolean = hasCodeModifierProperty(myCodeClass, name)
  override fun getTypeParameterList(): PsiTypeParameterList? = myCodeClass.typeParameterList
  override fun getTypeParameters(): Array<PsiTypeParameter> = myCodeClass.typeParameters

  override fun getImplementsListTypes(): Array<out PsiClassType> = myImplementsTypes
  override fun getExtendsListTypes(): Array<out PsiClassType> = myExtendsTypes

  override fun getSuperTypes(): Array<out PsiClassType> = mergeArrays(myExtendsTypes, myImplementsTypes)
  override fun getSuperClass(): PsiClass? = GrClassImplUtil.getSuperClass(myCodeClass, myExtendsTypes)
  override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = InheritanceImplUtil.isInheritor(this, baseClass, checkDeep)

  override fun getDelegate(): PsiClass = error("must not be called")
  override fun copy(): PsiElement = error("must not be called")
  override fun toString(): String = "[Hierarchy view] $name"
}
