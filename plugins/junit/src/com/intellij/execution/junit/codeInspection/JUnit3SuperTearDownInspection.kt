// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.isJUnit3InScope
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isInFinallyBlock
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class JUnit3SuperTearDownInspection : AbstractBaseUastLocalInspectionTool() {
  private fun shouldInspect(file: PsiFile) = isJUnit3InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      SuperTearDownInFinallyVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
  }
}

private class SuperTearDownInFinallyVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    if (node.receiver !is USuperExpression || node.methodName != "tearDown") return true
    val parentMethod = node.getParentOfType(
      UMethod::class.java, strict = true, terminators = arrayOf(ULambdaExpression::class.java, UDeclaration::class.java)
    ) ?: return true
    if (parentMethod.name != "tearDown") return true
    val containingClass = node.getContainingUClass()?.javaPsi ?: return true
    if (!InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)) return true
    if (node.isInFinallyBlock()) return true
    if (!hasNonTrivialActivity(parentMethod, node)) return true
    val message = JUnitBundle.message("jvm.inspections.junit3.super.teardown.problem.descriptor")
    holder.registerUProblem(node, message)
    return true
  }

  private fun hasNonTrivialActivity(parentMethod: UElement, node: UCallExpression): Boolean {
    val visitor = NonTrivialActivityVisitor(node)
    parentMethod.accept(visitor)
    return visitor.hasNonTrivialActivity
  }

  private class NonTrivialActivityVisitor(private val ignore: UCallExpression) : AbstractUastVisitor() {
    var hasNonTrivialActivity = false

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node == ignore) return true
      hasNonTrivialActivity = true
      return true
    }
  }
}