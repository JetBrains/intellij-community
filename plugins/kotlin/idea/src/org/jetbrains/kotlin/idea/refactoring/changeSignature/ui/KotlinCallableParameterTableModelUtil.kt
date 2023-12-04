// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.render
import org.jetbrains.kotlin.psi.KtPsiFactory

fun createRowItem(parameterInfo: KotlinParameterInfo?,
                  method: KotlinMethodDescriptor,
                  typeContext: PsiElement,
                  defaultValueContext: PsiElement): ParameterTableModelItemBase<KotlinParameterInfo> {
  val resultParameterInfo = parameterInfo ?: KotlinParameterInfo(
    callableDescriptor = method.baseDescriptor,
    name = "",
  )

  val psiFactory = KtPsiFactory(defaultValueContext.project)
  val paramTypeCodeFragment: PsiCodeFragment = psiFactory.createTypeCodeFragment(
    resultParameterInfo.currentTypeInfo.render(),
    typeContext,
  )

  val defaultValueCodeFragment: PsiCodeFragment = psiFactory.createExpressionCodeFragment(
    resultParameterInfo.defaultValueForCall?.text ?: "",
    defaultValueContext,
  )

  return object : ParameterTableModelItemBase<KotlinParameterInfo>(
    resultParameterInfo,
    paramTypeCodeFragment,
    defaultValueCodeFragment,
  ) {
    override fun isEllipsisType(): Boolean = false
  }
}