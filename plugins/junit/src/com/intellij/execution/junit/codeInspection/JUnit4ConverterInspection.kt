// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.execution.JUnitBundle
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_TEST
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnit4ConverterInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!PsiUtil.isLanguageLevel5OrHigher(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    if (JavaPsiFacade.getInstance(holder.project).findClass(JUNIT_FRAMEWORK_TEST_CASE, holder.file.resolveScope) == null) { // junit 3
      return PsiElementVisitor.EMPTY_VISITOR
    }
    if (JavaPsiFacade.getInstance(holder.project).findClass(ORG_JUNIT_TEST, holder.file.resolveScope) == null) { // junit 4
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnit4ConverterInspectionVisitor(holder),
      arrayOf(UClass::class.java),
      directOnly = true
    )
  }
}

private class JUnit4ConverterInspectionVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitClass(node: UClass): Boolean {
    val javaClass = node.javaPsi
    if (!InheritanceUtil.isInheritor(javaClass, JUNIT_FRAMEWORK_TEST_CASE)) return true
    val message = JUnitBundle.message("jvm.inspections.junit4.converter.problem.descriptor")
    holder.registerUProblem(node, message, JUnit4ConverterQuickfix())
    return true
  }
}