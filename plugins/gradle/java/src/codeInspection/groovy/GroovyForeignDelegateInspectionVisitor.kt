// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

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

fun getDelegationSourceCaller(hierarchy: DelegationHierarchy, resolved: PsiElement): GrMethodCall? {
  val psiClass = resolved.containingFile.asSafely<PsiClassOwner>()?.classes?.singleOrNull()?.takeIf {
    val qualifiedName = it.qualifiedName ?: return@takeIf false
    val returnType = resolved.asSafely<PsiMethod>()?.returnType?.resolve()

    qualifiedName.startsWith("org.gradle") &&
    qualifiedName != GradleCommonClassNames.GRADLE_API_PROJECT &&
    !InheritanceUtil.isInheritor(returnType, GradleCommonClassNames.GRADLE_API_TASK) &&
    !InheritanceUtil.isInheritor(returnType, GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER)
  } ?: return null
  for ((caller, containingInfo) in hierarchy.list) {
    if (containingInfo == null) {
      continue
    }
    if (retrievedFromDelegate(containingInfo, psiClass.qualifiedName!!)) {
      return caller
    }
  }
  return null
}


fun getDelegationHierarchy(place: PsiElement): DelegationHierarchy {
  val container = mutableListOf<Pair<GrMethodCall, PsiClass?>>()
  for (funExpr in place.parentsOfType<GrFunctionalExpression>()) {
    val containingCall = funExpr.parentOfType<GrMethodCall>() ?: continue
    if (containingCall.closureArguments.all { it != funExpr } && containingCall.expressionArguments.all { it != funExpr }) {
      continue
    }
    if (containingCall.resolveMethod()?.containingClass?.qualifiedName?.startsWith("org.gradle") != true) {
      continue
    }
    container.add(containingCall to getCallerClass(funExpr))
  }
  return DelegationHierarchy(container)
}

private fun getCallerClass(funExpr: GrFunctionalExpression): PsiClass? {
  val info = getDelegatesToInfo(funExpr) ?: return null
  if (!info.admitsDelegate()) {
    return null
  }
  return info.typeToDelegate?.asSafely<PsiClassType>()?.resolve()
}

private fun retrievedFromDelegate(first: PsiClass, name: @NlsSafe String) =
  InheritanceUtil.isInheritor(first, name)

private fun DelegatesToInfo.admitsDelegate(): Boolean {
  return strategy != Closure.OWNER_ONLY
}

@JvmInline
value class DelegationHierarchy(val list: List<Pair<GrMethodCall, PsiClass?>>)


