// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.java

import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import org.jetbrains.idea.devkit.threadingModelHelper.BaseLockReqRules
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqRules
import org.jetbrains.idea.devkit.threadingModelHelper.MethodSignature
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Actual visitor of bodies for the searched methods
 */
class JavaLockReqPsiOps : LockReqPsiOps {

  private val rules: LockReqRules = BaseLockReqRules()

  override fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    val callees = mutableListOf<PsiMethod>()

    method.body?.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
        expression.resolveMethod()?.let { resolved ->
          callees.add(resolved)
          val qualifierClass = resolved.containingClass?.qualifiedName
          if (qualifierClass == rules.disposerUtilityClassFqn && rules.disposeMethodNames.contains(resolved.name)) {
            collectDisposeTargets(expression)?.let { targets -> callees.addAll(targets) }
          }
        }
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        (expression.resolve() as? PsiMethod)?.let { callees.add(it) }
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }
    })

    return callees.distinct()
  }

  private fun collectDisposeTargets(call: PsiMethodCallExpression): List<PsiMethod>? {
    val args = call.argumentList.expressions
    if (args.isEmpty()) return null
    val argType = args.first().type as? PsiClassType ?: return null
    val psiClass = argType.resolve() ?: return null

    fun findZeroArgDispose(c: PsiClass): List<PsiMethod> = c.findMethodsByName("dispose", true)
      .filter { it.parameterList.parametersCount == 0 }

    val direct = findZeroArgDispose(psiClass)
    if (direct.isNotEmpty()) return direct

    val disposableFqn = rules.disposableInterfaceFqn
    val implementsDisposable = InheritanceUtil.isInheritor(psiClass, disposableFqn)
    if (implementsDisposable) {
      val project = call.project
      val disposableClass = JavaPsiFacade.getInstance(project)
        .findClass(disposableFqn, GlobalSearchScope.allScope(project))
      if (disposableClass != null) {
        val ifaceDispose = findZeroArgDispose(disposableClass)
        if (ifaceDispose.isNotEmpty()) return ifaceDispose
      }
    }
    return emptyList()
  }

  override fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit){
    if (method.containingClass?.hasAnnotation("java.lang.FunctionalInterface") == true) {
      return
    }
    if (method.body != null) {
      handler(method)
    }
    val counter = AtomicInteger(1)
    val query = OverridingMethodsSearch.search(method, scope, true).allowParallelProcessing()
    val list = Collections.synchronizedList(ArrayList<PsiMethod>())
    val abruptEnd = AtomicBoolean(false)
    blockingContextToIndicator {
      query.forEach(Processor { overridden ->
        val value = counter.incrementAndGet()
        if (value >= maxImpl) {
          abruptEnd.set(true)
          return@Processor false
        }
        list.add(overridden)
        true
      })
    }
    if (!abruptEnd.get()) {
      list.sortBy { it.name }
      for (method in list) {
        handler(method)
      }
    }
  }

  override fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit) {
    val query = ClassInheritorsSearch.search(interfaceClass, scope, true).allowParallelProcessing()
    val counter = AtomicInteger(1)
    val abruptEnd = AtomicBoolean(false)
    val list = Collections.synchronizedList(mutableListOf<PsiClass>())
    query.forEach(Processor { implementor ->
      if (counter.incrementAndGet() >= maxImpl) {
        abruptEnd.set(true)
        return@Processor false
      }
      list.add(implementor)
      true
    })
    if (!abruptEnd.get()) {
      list.sortBy { it.qualifiedName }
      for (clazz in list) {
        handler(clazz)
      }
    }
  }

  override fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean {
    return baseClassNames.any { baseClassName ->
      InheritanceUtil.isInheritor(psiClass, baseClassName)
    }
  }

  override fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean {
    return packagePrefixes.any { prefix -> className.startsWith("$prefix.") }
  }

  override fun resolveReturnType(method: PsiMethod): PsiClass? {
    val returnType = method.returnType as? PsiClassType ?: return null
    return returnType.resolve()
  }

  override fun extractTypeArguments(type: PsiType): List<PsiType> {
    return (type as? PsiClassType)?.parameters?.toList() ?: emptyList()
  }

  override fun extractSignature(element: PsiElement): MethodSignature {
    val method = element as PsiMethod
    return MethodSignature.fromMethod(method)
  }

  override fun extractTargetElement(file: PsiFile, caretOffset: Int): PsiMethod? {
    val elementAtCaret = file.findElementAt(caretOffset) ?: return null
    return elementAtCaret.parentOfType<PsiMethod>()
  }
}