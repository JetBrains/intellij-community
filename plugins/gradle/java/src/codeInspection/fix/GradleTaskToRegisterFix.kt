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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
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
    val typeArgument = inferClassArgument(callParent)
    val dependsOnArgument = inferDependsOnArgument(callParent)
    val closure = buildClosure(project, dependsOnArgument, secondArgument)
    val representation = buildString {
      append("tasks.register('$firstArgument'")
      if (typeArgument != null) {
        append(", ${typeArgument.text}")
      }
      append(when (closure) {
        null -> ")" // no closure
        is GrClosableBlock -> ")" + closure.text // a closable block can be moved out of parentheses
        String -> ", $closure)" // a random expression can be in parentheses
        else -> error("Unexpected object: $closure")
      })
    }
    val newCall = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(representation)
    callParent.replace(newCall)
  }

  private fun buildClosure(project: Project, dependsOn: PsiElement?, closureArgument: PsiElement?) : Any? {
    val factory = GroovyPsiElementFactory.getInstance(project)
    return when (closureArgument) {
      null -> if (dependsOn == null) null else enhanceClosure(dependsOn, factory.createClosureFromText(" { }"), factory)
      is GrClosableBlock -> enhanceClosure(dependsOn, closureArgument, factory)
      else -> if (dependsOn == null) closureArgument.text else enhanceClosure(dependsOn, factory.createClosureFromText("{}"), factory)
    }
  }

  private fun enhanceClosure(dependsOn: PsiElement?, block: GrClosableBlock, factory: GroovyPsiElementFactory) : GrClosableBlock {
    if (dependsOn == null) return block
    val copy = block.copy() as GrClosableBlock
    val lbrace = copy.lBrace
    copy.addAfter(factory.createExpressionFromText("dependsOn ${dependsOn.text}"), lbrace)
    return copy
  }

  private fun inferFirstArgument(callParent: GrMethodCall): String? {
    val argument = callParent.expressionArguments.firstOrNull() ?: return null
    return when (argument) {
      is GrMethodCallExpression -> argument.invokedExpression.text
      is GrLiteral -> argument.stringValue()
      is GrReferenceExpression -> argument.text
      else -> null
    }
  }

  private fun inferSecondArgument(callParent: GrMethodCall, inspectFirstArg: Boolean = true): PsiElement? {
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

  private fun inferClassArgument(callParent: GrMethodCall): PsiElement? = extractNamedArgument(callParent, "type")

  private fun inferDependsOnArgument(callParent: GrMethodCall): PsiElement? = extractNamedArgument(callParent, "dependsOn")

  private fun extractNamedArgument(topCall: GrMethodCall, label: String) : PsiElement? {
    return topCall.doExtractNamedArgument(label)
           ?: topCall.expressionArguments.firstOrNull()?.doExtractNamedArgument(label)
  }

  private fun GrExpression.doExtractNamedArgument(labelName: String) : PsiElement? {
    if (this !is GrMethodCallExpression) {
      return null
    }
    return namedArguments.find { it.labelName == labelName }?.expression
  }
}