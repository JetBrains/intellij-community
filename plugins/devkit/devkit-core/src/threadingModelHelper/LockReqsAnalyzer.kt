// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiMethodReferenceExpression

class LockReqsAnalyzer {

  companion object {

    private const val ASSERT_READ_ACCESS_METHOD = "assertReadAccess"
    private const val THREADING_ASSERTIONS_CLASS = "com.intellij.util.concurrency.ThreadingAssertions"
    private const val REQUIRES_READ_LOCK_ANNOTATION = "com.intellij.util.concurrency.annotations.RequiresReadLock"
    private const val MAX_PATH_DEPTH = 1000

    enum class LockCheckType {
      ANNOTATION,
      ASSERTION
    }

    data class LockRequirement(
      val type: LockCheckType,
      val method: PsiMethod,
    )

    data class ExecutionPath(
      val methodChain: List<PsiMethod>,
      val lockRequirement: LockRequirement,
    ) {
      val pathString: String
        get() = buildString {
          append(methodChain.joinToString(" -> ") {
            "${it.containingClass?.name}.${it.name}" +
            when (lockRequirement.type) {
              LockCheckType.ANNOTATION -> "@RequiresReadLock"
              LockCheckType.ASSERTION -> "ThreadingAssertions.assertReadAccess()"
            }
          })
        }
    }
  }

  private val processed = mutableSetOf<PsiMethod>()

  fun analyzeMethod(method: PsiMethod): List<ExecutionPath> {
    processed.clear()
    val paths = mutableListOf<ExecutionPath>()
    val currentPath = mutableListOf<PsiMethod>()
    processMethodDFS(method, currentPath, paths)
    return paths
  }

  private fun processMethodDFS(
    method: PsiMethod,
    currentPath: MutableList<PsiMethod>,
    paths: MutableList<ExecutionPath>,
  ) {

    if (method in processed || currentPath.size > MAX_PATH_DEPTH) return
    processed.add(method)
    currentPath.add(method)
    findLockChecks(method).forEach { check ->
      paths.add(ExecutionPath(currentPath.toList(), LockRequirement(check, method)))
    }
    getMethodCallees(method).forEach { callee -> processMethodDFS(callee, currentPath, paths) }
    currentPath.removeAt(currentPath.lastIndex)
  }

  private fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    val callees = mutableListOf<PsiMethod>()
    method.body?.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        (expression.resolve() as? PsiMethod)?.let { callees.add(it) }
      }
    })
    return callees
  }

  private fun findLockChecks(method: PsiMethod): List<LockCheckType> {
    return buildList {
      if (hasRequiresReadLockAnnotation(method)) add(LockCheckType.ANNOTATION)
      if (hasAssertReadAccessCall(method)) add(LockCheckType.ASSERTION)
    }
  }

  private fun hasRequiresReadLockAnnotation(method: PsiMethod): Boolean {
    return method.hasAnnotation(REQUIRES_READ_LOCK_ANNOTATION)
  }

  private fun isAssertReadAccess(expression: PsiMethodCallExpression): Boolean {
    return ASSERT_READ_ACCESS_METHOD == expression.methodExpression.referenceName &&
           THREADING_ASSERTIONS_CLASS == expression.resolveMethod()?.containingClass?.qualifiedName
  }

  private fun hasAssertReadAccessCall(method: PsiMethod): Boolean {
    var found = false
    method.body?.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        if (!found) {
          super.visitMethodCallExpression(expression)
          if (isAssertReadAccess(expression)) found = true
        }
      }
    })
    return found
  }
}
