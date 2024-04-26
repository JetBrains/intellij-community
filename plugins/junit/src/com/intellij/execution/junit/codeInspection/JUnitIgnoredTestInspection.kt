// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.isJUnit4InScope
import com.intellij.execution.junit.isJUnit5InScope
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_IGNORE
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_DISABLED
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnitIgnoredTestInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  var onlyReportWithoutReason = true

  override fun getOptionsPane() = pane(checkbox("onlyReportWithoutReason", JUnitBundle.message("jvm.inspections.junit.ignored.test.ignore.reason.option")))

  private fun shouldInspect(file: PsiFile) = isJUnit4InScope(file) || isJUnit5InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitIgnoredTestVisitor(holder, onlyReportWithoutReason),
      arrayOf(UClass::class.java, UMethod::class.java),
      directOnly = true
    )
  }
}

private class JUnitIgnoredTestVisitor(
  private val holder: ProblemsHolder,
  private val onlyReportWithoutReason: Boolean
) : AbstractUastNonRecursiveVisitor() {
  val withoutReasonChoice = if (onlyReportWithoutReason) 2 else 1

  override fun visitClass(node: UClass): Boolean = checkIgnoreOrDisabled(
    node, JUnitBundle.message("jvm.inspections.junit.ignored.test.class.problem.descriptor", node.javaPsi.name, withoutReasonChoice)
  )

  override fun visitMethod(node: UMethod): Boolean = checkIgnoreOrDisabled(
    node, JUnitBundle.message("jvm.inspections.junit.ignored.test.method.problem.descriptor", node.name, withoutReasonChoice)
  )

  private fun checkIgnoreOrDisabled(node: UDeclaration, message: @InspectionMessage String): Boolean {
    val annotations = node.findAnnotations(ORG_JUNIT_IGNORE, ORG_JUNIT_JUPITER_API_DISABLED)
    if (annotations.isEmpty()) return true
    if (onlyReportWithoutReason && annotations.any {
      it.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) != null
    }) return true
    holder.registerUProblem(node, message)
    return true
  }
}