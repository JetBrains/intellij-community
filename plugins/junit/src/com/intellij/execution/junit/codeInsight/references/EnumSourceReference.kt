// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight.references

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.annotation.JvmAnnotationClassValue
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

class EnumSourceReference(element: PsiLiteral) : PsiReferenceBase<PsiLiteral>(element, false) {
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
    val enumClass = findReferencedEnumClass(element) ?: return null
    val literalValue = value
    return enumClass.fields.asSequence()
      .filterIsInstance(PsiEnumConstant::class.java)
      .firstOrNull() { it.name == literalValue }
  }

  override fun getVariants(): Array<Any> {
    val enumClass = findReferencedEnumClass(element) ?: return emptyArray()
    return enumClass.fields.asSequence()
      .filterIsInstance(PsiEnumConstant::class.java)
      .map { createLookupBuilder(it) }
      .toList()
      .toTypedArray()
  }

  private fun findReferencedEnumClass(literal: PsiLiteral): JvmClass? {
    val annotationParameters = PsiTreeUtil.getParentOfType(literal, PsiAnnotationParameterList::class.java, false)
                               ?: return null
    val valuePair = annotationParameters.attributes.firstOrNull { it.name == PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME }
    return (valuePair?.attributeValue as? JvmAnnotationClassValue)?.clazz;
  }

  private fun createLookupBuilder(psiEnum: PsiEnumConstant): LookupElement =
    LookupElementBuilder.create(psiEnum).withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT)
}
