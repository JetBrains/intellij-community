// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.*
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.isJUnit4InScope
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.util.*

class TestCaseWithMultipleRunnersInspection : AbstractBaseUastLocalInspectionTool() {
  private fun shouldInspect(file: PsiFile) = isJUnit4InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastVisitorAdapter(RunWithVisitor(holder), true)
  }
}

private class RunWithVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitClass(node: UClass): Boolean {
    val runWiths = node.findAnnotations(TestUtils.RUN_WITH)
    if (runWiths.isEmpty()) return true

    val queue: Queue<PsiClass> = ArrayDeque<PsiClass>().apply { addAll(node.javaPsi.supers) }
    while (queue.isNotEmpty()) {
      val parent = queue.poll()
      if (parent.hasAnnotation(TestUtils.RUN_WITH)) {
        val message = JUnitBundle.message("jvm.inspections.junit4.inherited.runwith.problem.descriptor", parent.name)
        holder.registerUProblem(runWiths[0], message, highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        return true
      }
      queue.addAll(parent.supers)
    }
    return true
  }
}