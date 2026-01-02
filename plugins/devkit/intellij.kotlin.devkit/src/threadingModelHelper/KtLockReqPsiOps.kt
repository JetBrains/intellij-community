// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.Processor
import org.jetbrains.idea.devkit.threadingModelHelper.BaseLockReqRules
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqRules
import org.jetbrains.idea.devkit.threadingModelHelper.MethodSignature
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KtLockReqPsiOps : LockReqPsiOps {


  override fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    return resolveCalleesWithUast(method).distinct()
  }

  override fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit) {
    handler(method)
    val counter = AtomicInteger(1)
    val list = Collections.synchronizedList(mutableListOf<PsiMethod>())
    val abruptEnd: AtomicBoolean = AtomicBoolean(false)
    OverridingMethodsSearch.search(method, scope, true).allowParallelProcessing()
      .forEach(Processor { overridden ->
        if (counter.incrementAndGet() >= maxImpl) {
          //println("Too many inheritors for ${method.name}, stopping")
          abruptEnd.set(true)
          return@Processor false
        }
        list.add(overridden)
        true
      })
    if (!abruptEnd.get()) {
      list.sortBy { it.name }
      for (method in list) {
        handler(method)
      }
    }
  }

  override fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit) {
    val counter = AtomicInteger(1)
    val list = Collections.synchronizedList(mutableListOf<PsiClass>())
    val abruptEnd: AtomicBoolean = AtomicBoolean(false)
    ClassInheritorsSearch.search(interfaceClass, scope, true)
      .forEach(Processor { implementor ->
        if (counter.incrementAndGet() >= maxImpl) {
          //println("Too many implementations for ${interfaceClass.name}, stopping")
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
    return baseClassNames.any { base -> InheritanceUtil.isInheritor(psiClass, base) }
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
    val method = element.getUastParentOfType<UMethod>()!!.javaPsi
    return MethodSignature.fromMethod(method)
  }

  override fun extractTargetElement(file: PsiFile, caretOffset: Int): PsiMethod? {
    if (file !is KtFile) {
      return null
    }
    val elementAtOffset = file.findElementAt(caretOffset)
    val uMethod = elementAtOffset.getUastParentOfType<UMethod>(false)
    return uMethod?.javaPsi
  }

  private val rules: LockReqRules = BaseLockReqRules()

  private fun resolveCalleesWithUast(method: PsiMethod): List<PsiMethod> {
    val set = LinkedHashSet<PsiMethod>()
    val uMethod = method.toUElement(UMethod::class.java) ?: return emptyList()
    uMethod.accept(object : AbstractUastVisitor() {

      override fun visitExpression(node: UExpression): Boolean {
        node.tryResolve()?.let { resolved ->
          if (resolved !is PsiMethod) {
            return@let
          }
          set.add(resolved)
          val qName = resolved.containingClass?.qualifiedName
          if (node is UCallExpression && qName == rules.disposerUtilityClassFqn && rules.disposeMethodNames.contains(resolved.name)) {
            disposeTargets(node).forEach { set.add(it) }
          }
        }
        return super.visitExpression(node)
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
    if (InheritanceUtil.isInheritor(psiClass, disposableFqn)) {
      val project = (node.sourcePsi ?: return emptyList()).project
      val disposableClass = com.intellij.psi.JavaPsiFacade.getInstance(project)
        .findClass(disposableFqn, com.intellij.psi.search.GlobalSearchScope.allScope(project))
      if (disposableClass != null) return zeroArgDispose(disposableClass)
    }
    return emptyList()
  }
}