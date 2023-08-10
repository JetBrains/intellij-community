// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.idea.devkit.inspections.UElementAsPsiCheckProvider
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.KotlinReceiverUParameter

internal class KtUElementAsPsiCheckProvider : UElementAsPsiCheckProvider {

  override fun isPsiElementReceiver(uMethod: UMethod): Boolean {
    val psi = uMethod.sourcePsi ?: return false
    val receiverType = uMethod.getReceiverType() ?: return false
    val psiElementType = getClassType<PsiElement>(psi) ?: return false
    val uElementType = getClassType<UElement>(psi) ?: return false
    return !TypeConversionUtil.isAssignable(uElementType, receiverType) && TypeConversionUtil.isAssignable(psiElementType, receiverType)
  }

  private inline fun <reified T : Any> getClassType(context: PsiElement):PsiClassType? {
    return PsiType.getTypeByName(T::class.java.name, context.project, context.resolveScope).takeIf { it.resolve() != null }
  }

  private fun UMethod.getReceiverType(): PsiType? {
    val receiver = this.uastParameters.firstOrNull() as? KotlinReceiverUParameter ?: return null
    return receiver.typeReference?.type
  }
}
