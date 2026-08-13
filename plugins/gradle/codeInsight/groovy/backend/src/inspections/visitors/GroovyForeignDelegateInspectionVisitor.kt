// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.codeInsight.GradleInspectionBundle
import com.intellij.gradle.java.groovy.codeInspection.getDelegationHierarchy
import com.intellij.gradle.java.groovy.codeInspection.getDelegationSourceCaller
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GroovyForeignDelegateInspectionVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    val callQualifier = call.invokedExpression.asSafely<GrReferenceExpression>()
    if (callQualifier != null && callQualifier.qualifierExpression != null) {
      return
    }
    val resolvedMethod = call.resolveMethod() ?: return
    resolvedMethod.containingClass?.takeIf { it.qualifiedName?.startsWith("org.gradle") == true } ?: return
    val hierarchy = getDelegationHierarchy(call)
    val definingCaller = getDelegationSourceCaller(hierarchy, resolvedMethod)
    if (definingCaller == null || definingCaller == hierarchy.list.firstOrNull()?.first) {
      return
    }
    val refExpr = call.invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement ?: return
    val callerRefExpr = definingCaller.invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement ?: return
    val enclosingRefCall = hierarchy.list.first().first.invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement
                           ?: return
    holder.registerProblem(refExpr, GradleInspectionBundle.message("inspection.message.0.defined.by.1.but.used.within.2", refExpr.text,
                                                                   callerRefExpr.text, enclosingRefCall.text),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    super.visitMethodCall(call)
  }
}
