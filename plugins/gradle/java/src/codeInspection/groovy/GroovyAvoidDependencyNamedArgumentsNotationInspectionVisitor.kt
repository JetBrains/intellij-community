// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.OriginInfoAwareElement
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    val method = call.resolveMethod() ?: return
    if (method !is OriginInfoAwareElement || method.originInfo != GradleDependencyHandlerContributor.DEPENDENCY_NOTATION) {
      return
    }
    val arguments = call.argumentList.expressionArguments
    val namedArguments = call.namedArguments
    if (arguments.isNotEmpty()) return
    if (namedArguments.size != 3) return
    if (namedArguments.map { it.labelName }.intersect(listOf("group", "name", "version")).size != 3) return

    holder.registerProblem(
      call.argumentList,
      GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
      ProblemHighlightType.WEAK_WARNING
    )
  }
}