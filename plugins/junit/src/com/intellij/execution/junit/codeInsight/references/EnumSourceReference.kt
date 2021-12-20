// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight.references

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.IncorrectOperationException

class EnumSourceReference(element: PsiLanguageInjectionHost) : PsiReferenceBase<PsiLanguageInjectionHost>(element, false) {
  @Throws(IncorrectOperationException::class)
  override fun bindToElement(element: PsiElement): PsiElement {
    return if (element is PsiEnumConstant) {
      handleElementRename(element.name)
    }
    else {
      super.bindToElement(element)
    }
  }

  override fun resolve(): PsiElement? {
    return (findReferencedEnumClass(element) ?: return null).findFieldByName(value, false)
  }

  override fun getVariants(): Array<Any> {
    val enumClass = findReferencedEnumClass(element) ?: return emptyArray()
    return enumClass.fields
      .filterIsInstance<PsiEnumConstant>()
      .filter { filterDuplicated(it) }
      .map { LookupElementBuilder.create(it).withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT) }
      .toTypedArray()
  }

  private fun filterDuplicated(constanta: PsiEnumConstant): Boolean {
    when {
      element.parent is PsiArrayInitializerMemberValue -> {
        val initializers = (element.parent as PsiArrayInitializerMemberValue).initializers
        return !initializers.filterIsInstance<PsiLiteralExpression>().map { it.value }.toSet().contains(constanta.name)
      }
      else -> return true
    }
  }

  private fun findReferencedEnumClass(literal: PsiLanguageInjectionHost): PsiClass? {
    val annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java, false)
                     ?: return null
    val memberValue = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
    return PsiUtil.resolveClassInClassTypeOnly((memberValue as? PsiClassObjectAccessExpression)?.operand?.type);
  }

}
