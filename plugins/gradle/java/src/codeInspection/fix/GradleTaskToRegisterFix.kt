// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue

class GradleTaskToRegisterFix : LocalQuickFix {
  override fun getFamilyName(): String {
    return GroovyBundle.message("intention.family.name.replace.keywords")
  }

  override fun getName(): String {
    return GradleInspectionBundle.message("intention.name.use.tasks.register")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val callParent = descriptor.psiElement.parentOfType<GrMethodCall>() ?: return
    val firstArgument = inferFirstArgument(callParent) ?: return
    val secondArgument = inferSecondArgument(callParent)
    val representation = "tasks.register" + when (secondArgument) {
      null -> "('$firstArgument')"
      is GrClosableBlock -> "('$firstArgument') ${secondArgument.text}"
      else -> "('$firstArgument', ${secondArgument.text})"
    }
    val newCall = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(representation)
    callParent.replace(newCall)
  }

  private fun inferFirstArgument(callParent: GrMethodCall) : String? {
    val argument = callParent.expressionArguments.firstOrNull() ?: return null
    return when (argument) {
      is GrMethodCallExpression -> argument.invokedExpression.text
      is GrLiteral -> argument.stringValue()
      is GrReferenceExpression -> argument.text
      else -> null
    }
  }

  private fun inferSecondArgument(callParent: GrMethodCall, inspectFirstArg: Boolean = true) : PsiElement? {
    val closureArgument = callParent.closureArguments.firstOrNull()
    if (closureArgument != null) {
      return closureArgument
    }
    val argument = callParent.expressionArguments.getOrNull(1)
    if (argument != null) {
      return argument
    }
    if (inspectFirstArg) {
      val firstArgument = callParent.expressionArguments.firstOrNull()?.asSafely<GrMethodCall>() ?: return null
      return inferSecondArgument(firstArgument, false)
    }
    return null
  }
}