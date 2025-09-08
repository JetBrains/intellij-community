// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.CommonClassNames
import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleDependencyNamedArgumentsFix
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    val method = call.resolveMethod() ?: return
    if (method !is OriginInfoAwareElement || method.originInfo != GradleDependencyHandlerContributor.DEPENDENCY_NOTATION) {
      return
    }
    val arguments = call.argumentList.expressionArguments
    if (arguments.isEmpty()) {
      if (!isReplaceableWithSingleString(call.namedArguments.asList())) return

      holder.registerProblem(
        call.argumentList,
        GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        GradleDependencyNamedArgumentsFix()
      )
    }
    else {
      for (argument in arguments) {
        if (InheritanceUtil.isInheritor(argument.type, CommonClassNames.JAVA_UTIL_MAP) && argument is GrListOrMap) {
          if (!isReplaceableWithSingleString(argument.namedArguments.asList())) continue

          holder.registerProblem(
            argument,
            GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            GradleDependencyNamedArgumentsFix()
          )
        }
      }
    }
  }

  private fun isReplaceableWithSingleString(namedArguments: List<GrNamedArgument>): Boolean {
    val namedArgumentsNames = namedArguments.map { it.labelName }
    when (namedArgumentsNames.size) {
      2 -> if (!namedArgumentsNames.containsAll(setOf("group", "name"))) return false
      3 -> if (!namedArgumentsNames.containsAll(setOf("group", "name", "version"))) return false
      else -> return false
    }
    // check that all named arguments are string literals
    for (argument in namedArguments.map { it.expression }) {
      if (argument !is GrLiteral) return false
      val type = argument.type ?: return false
      if (
        !InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_CHAR_SEQUENCE) &&
        !type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING)
      ) return false
    }
    return true
  }
}