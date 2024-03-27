// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.reference.PsiMemberReference
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType

class EnumSourceReference(element: PsiLanguageInjectionHost) : PsiReferenceBase<PsiLanguageInjectionHost>(element, false), PsiMemberReference {
  override fun bindToElement(element: PsiElement): PsiElement {
    if (element is PsiEnumConstant) {
      handleElementRename(element.name)
    }
    return super.bindToElement(element)
  }

  override fun resolve(): PsiElement? = findReferencedEnumClass(element)?.findFieldByName(value, false)

  override fun getVariants(): Array<Any> {
    val enumClass = findReferencedEnumClass(element) ?: return emptyArray()
    return enumClass.fields
      .filterIsInstance<PsiEnumConstant>()
      .filter { filterDuplicated(it) }
      .map { LookupElementBuilder.create(it).withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT) }
      .toTypedArray()
  }

  private fun filterDuplicated(constanta: PsiEnumConstant): Boolean {
    when (element.parent) {
      is PsiArrayInitializerMemberValue -> {
        val initializers = (element.parent as PsiArrayInitializerMemberValue).initializers
        return !initializers.filterIsInstance<PsiLiteralExpression>().map { it.value }.toSet().contains(constanta.name)
      }
      else -> return true
    }
  }

  private fun findReferencedEnumClass(literal: PsiLanguageInjectionHost): PsiClass? {
    val expressionToFind = literal.toUElementOfType<UInjectionHost>()
      ?.getParentOfType<UAnnotation>()
      ?.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
      as UClassLiteralExpression

    return PsiUtil.resolveClassInClassTypeOnly(expressionToFind.type)
  }
}
