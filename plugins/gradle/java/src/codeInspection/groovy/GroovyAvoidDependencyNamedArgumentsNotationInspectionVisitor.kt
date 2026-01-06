// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleDependencyNamedArgumentsFix
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor.Companion.dependencyMethodKind
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind

class GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(private val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    if (!DEPENDENCY_CALL_PATTERN.accepts(call) && !SPECIAL_DEPENDENCY_CALL_PATTERN.accepts(call)) return
    val arguments = call.argumentList.expressionArguments
    if (arguments.isEmpty()) {
      if (hasUnexpectedNamedArguments(call.namedArguments.asList())) return

      holder.problem(
        call,
        GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
      ).range(call.argumentList.textRangeInParent)
        .maybeFix(GradleDependencyNamedArgumentsFix.createFixIfPossible(call.argumentList))
        .register()
    }
    else {
      for (argument in arguments) {
        if (argument !is GrListOrMap || !argument.isMap) continue
        if (hasUnexpectedNamedArguments(argument.namedArguments.asList())) continue

        holder.problem(
          argument,
          GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
        ).maybeFix(GradleDependencyNamedArgumentsFix.createFixIfPossible(argument))
          .register()
      }
    }
  }

  private fun hasUnexpectedNamedArguments(namedArguments: List<GrNamedArgument>): Boolean {
    val namedArgumentsNames = namedArguments.map { it.labelName }.toSet()
    return !namedArgumentsNames.containsAll(setOf("group", "name"))
  }

  companion object {
    private val DEPENDENCY_CALL_PATTERN = GroovyMethodCallPattern
      .resolvesTo(psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withKind(dependencyMethodKind))
    private val SPECIAL_DEPENDENCY_CALL_PATTERN = GroovyMethodCallPattern
      .resolvesTo(psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withName("platform", "enforcedPlatform", "testFixtures"))
  }
}