// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.GrBlockLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GroovyPluginDslStructureInspectionVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitFile(file: GroovyFileBase) {
    if (!file.isGradleFile()) return
    val statements = file.statements
    val lastPluginsStatement = statements.indexOfFirst { it is GrMethodCall && it.invokedExpression.text == "plugins" }
    if (lastPluginsStatement == -1) {
      return
    }
    val pluginsStatement = statements[lastPluginsStatement] as GrMethodCall
    checkPluginsStatement(holder, pluginsStatement)
    val statementsToCheck = statements.asList().subList(0, lastPluginsStatement)
    for (suspiciousStatement in statementsToCheck) {
      val psiToHighlight = getBadStatementHighlightingElement(suspiciousStatement) ?: continue
      holder.registerProblem(psiToHighlight, GradleInspectionBundle.message("inspection.message.incorrect.buildscript.structure"), ProblemHighlightType.GENERIC_ERROR)
    }
  }
}


private fun checkPluginsStatement(holder: ProblemsHolder, pluginsStatement: GrMethodCall) {
  val statements = getStatements(pluginsStatement)
  for (statement in statements) {
    if (statement !is GrMethodCall) {
      holder.registerProblem(statement, GradleInspectionBundle.message("inspection.message.only.method.calls.to.id.alias.are.available.in.plugins.block"))
      continue
    }
    val (refElement) = decomposeCall(statement) ?: continue
    when (refElement?.text) {
      "apply" -> checkApply(holder, statement)
      "version" -> checkVersion(holder, statement)
      else -> if (checkIdAlias(holder, statement)) {
        holder.registerProblem(statement, GradleInspectionBundle.message("inspection.message.only.method.calls.to.id.alias.are.available.in.plugins.block"))
      }
    }
  }
}

private fun decomposeCall(call: GrMethodCall) : Triple<PsiElement?, GrExpression?, String?>? {
  val caller = call.invokedExpression.asSafely<GrReferenceExpression>() ?: return null
  val qualifierCallName = caller.qualifierExpression.asSafely<GrMethodCall>()?.invokedExpression.asSafely<GrReferenceExpression>()?.referenceName
  return Triple(caller.referenceNameElement, caller.qualifierExpression, qualifierCallName)
}

private fun checkApply(holder: ProblemsHolder, method: GrExpression?) {
  if (method !is GrMethodCall) {
    return
  }
  val (refElement, qualifier, qualifierCallName) = decomposeCall(method) ?: return
  if (qualifierCallName == "version") {
    checkVersion(holder, qualifier)
  } else if (refElement != null && checkIdAlias(holder, qualifier)) {
    holder.registerProblem(refElement, GradleInspectionBundle.message("inspection.message.only.method.calls.to.id.alias.are.available.in.plugins.block"))
  }
}

private fun checkVersion(holder: ProblemsHolder, method: GrExpression?) {
  if (method !is GrMethodCall) {
    return
  }
  val (refElement, qualifier) = decomposeCall(method) ?: return
  if (refElement != null && checkIdAlias(holder, qualifier)) {
    holder.registerProblem(refElement, GradleInspectionBundle.message("inspection.message.only.method.calls.to.id.alias.are.available.in.plugins.block"))
  }
}

private fun checkIdAlias(holder: ProblemsHolder, method: GrExpression?) : Boolean {
  if (method !is GrMethodCall) {
    return true
  }
  val (refElement, _) = decomposeCall(method) ?: return true
  val methodName = refElement?.text
  if (methodName != "id" && methodName != "alias") {
    holder.registerProblem(refElement ?: method, GradleInspectionBundle.message("inspection.message.only.method.calls.to.id.alias.are.available.in.plugins.block"))
  }
  return false
}



private val allowedStrings = setOf("buildscript", "pluginManagement", "plugins")

private fun getBadStatementHighlightingElement(suspiciousStatement: GrStatement): PsiElement? {
  if (suspiciousStatement !is GrMethodCall) {
    return suspiciousStatement
  }
  if (suspiciousStatement.invokedExpression.text !in allowedStrings) {
    return suspiciousStatement.invokedExpression
  }
  return null
}

private fun getStatements(call: GrMethodCall) : Array<GrStatement> {
  val closure = call.closureArguments.singleOrNull() ?: call.expressionArguments.firstOrNull()?.asSafely<GrFunctionalExpression>() ?: return emptyArray()
  return getStatements(closure)
}

private fun getStatements(funExpr: GrFunctionalExpression) : Array<GrStatement> {
  return when (funExpr) {
    is GrClosableBlock -> return funExpr.statements
    is GrLambdaExpression -> when (val body = funExpr.body) {
      is GrBlockLambdaBody -> body.statements
      is GrExpressionLambdaBody -> arrayOf(body.expression)
      else -> emptyArray()
    }
    else -> emptyArray()
  }
}