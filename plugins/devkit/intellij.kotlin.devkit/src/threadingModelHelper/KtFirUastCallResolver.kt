// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor


class KtFirUastCallResolver : KtCallResolver {

  override fun resolveCallees(method: PsiMethod): List<PsiMethod> {
    return resolveCalleesWithUast(method)
  }

  override fun resolveReturnPsiClass(method: PsiMethod): PsiClass? {
    val returnType = method.returnType as? PsiClassType ?: return null
    return returnType.resolve()
  }

  private fun resolveCalleesWithUast(method: PsiMethod): List<PsiMethod> {
    val set = LinkedHashSet<PsiMethod>()
    val uMethod = method.toUElement(UMethod::class.java) ?: return emptyList()
    uMethod.accept(object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        node.resolve()?.let { set.add(it) }
        return super.visitCallExpression(node)
      }
      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
        (node.resolve() as? PsiMethod)?.let { set.add(it) }
        return super.visitCallableReferenceExpression(node)
      }
    })
    return set.toList()
  }


}
