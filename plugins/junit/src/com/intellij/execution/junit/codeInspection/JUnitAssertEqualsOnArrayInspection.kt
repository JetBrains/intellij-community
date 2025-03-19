// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.*
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.isJUnit4InScope
import com.intellij.execution.junit.isJUnit5InScope
import com.intellij.jvm.analysis.quickFix.ReplaceCallableExpressionQuickFix
import com.intellij.jvm.analysis.refactoring.CallChainReplacementInfo
import com.intellij.jvm.analysis.refactoring.CallReplacementInfo
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnitAssertEqualsOnArrayInspection : AbstractBaseUastLocalInspectionTool(), CleanupLocalInspectionTool {
  private fun shouldInspect(file: PsiFile) = isJUnit4InScope(file) || isJUnit5InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitAssertEqualsOnArrayVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
  }
}

private class JUnitAssertEqualsOnArrayVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    val assertHint = UAssertHint.createAssertEqualsHint(node)
    val containingClassForDecl = node.resolve()?.containingClass ?: return true
    if (containingClassForDecl.qualifiedName?.contains("junit") != true) return true // no assertArrayEquals for testng
    val firstArgType = assertHint?.firstArgument?.getExpressionType() ?: return true
    val sectArgType = assertHint.secondArgument.getExpressionType() ?: return true
    if (firstArgType !is PsiArrayType || sectArgType !is PsiArrayType) return true
    val message = JUnitBundle.message("jvm.inspections.junit.assertequals.on.array.problem.descriptor")
    holder.registerUProblem(node, message, ReplaceCallableExpressionQuickFix(CallChainReplacementInfo(
      null, CallReplacementInfo("assertArrayEquals", null, *node.valueArguments.toTypedArray()))))
    return true
  }
}