// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.idea.devkit.threadingModelHelper.BaseLockReqRules
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqRules

class KtFirUastCallResolver(private val rules: LockReqRules = BaseLockReqRules()) : KtCallResolver {

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
        node.resolve()?.let { resolved ->
          set.add(resolved)
          val qName = resolved.containingClass?.qualifiedName
          if (qName == rules.disposerUtilityClassFqn && rules.disposeMethodNames.contains(resolved.name)) {
            disposeTargets(node).forEach { set.add(it) }
          }
        }
        return super.visitCallExpression(node)
      }
      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
        (node.resolve() as? PsiMethod)?.let { set.add(it) }
        return super.visitCallableReferenceExpression(node)
      }
    })
    return set.toList()
  }

  private fun disposeTargets(node: UCallExpression): List<PsiMethod> {
    val arg = node.valueArguments.firstOrNull() ?: return emptyList()
    val psiType = arg.getExpressionType() as? PsiClassType ?: return emptyList()
    val psiClass = psiType.resolve() ?: return emptyList()

    fun zeroArgDispose(c: PsiClass): List<PsiMethod> = c.findMethodsByName("dispose", true)
      .filter { it.parameterList.parametersCount == 0 }

    val direct = zeroArgDispose(psiClass)
    if (direct.isNotEmpty()) return direct

    val disposableFqn = rules.disposableInterfaceFqn
    if (com.intellij.psi.util.InheritanceUtil.isInheritor(psiClass, disposableFqn)) {
      val project = (node.sourcePsi ?: return emptyList()).project
      val disposableClass = com.intellij.psi.JavaPsiFacade.getInstance(project)
        .findClass(disposableFqn, com.intellij.psi.search.GlobalSearchScope.allScope(project))
      if (disposableClass != null) return zeroArgDispose(disposableClass)
    }
    return emptyList()
  }
}
