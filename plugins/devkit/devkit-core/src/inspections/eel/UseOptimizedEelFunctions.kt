// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

@Suppress("HardCodedStringLiteral")
class UseOptimizedEelFunctions : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
      private val visitedCalls = hashSetOf<UExpression>()

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        val selector = node.selector
        if (selector is UCallExpression) {
          return visitCallExpression(selector)
        }

        return false
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (visitedCalls.add(node)) {
          val methodName = node.methodName ?: return true

          val receiverName = node.receiver
            ?.getQualifiedChain()
            ?.lastOrNull()
            ?.let { it as? UReferenceExpression }
            ?.getQualifiedName()

          val fqn = if (receiverName != null) {
            "$receiverName.$methodName"
          }
          else {
            // Handle static imports and aliases: resolve the method to get its fully qualified name
            val method = node.resolve() ?: return true
            val containingClass = method.containingClass?.qualifiedName ?: return true
            val actualMethodName = method.name
            "$containingClass.$actualMethodName"
          }

          handleMethod(holder, node, fqn)
        }

        return true
      }
    }, true)

  private fun handleMethod(holder: ProblemsHolder, node: UExpression, fqn: String) {
    val methodPsi = when (node) {
      is UCallExpression -> node.methodIdentifier?.sourcePsi ?: return
      else -> node.sourcePsi ?: return
    }
    if (fqn == "java.nio.file.Files.readAllBytes") {
      holder.registerProblem(
        methodPsi, "Works ineffectively with remote Eel", ProblemHighlightType.WARNING,
        ReplaceWithEelFunction("com.intellij.platform.eel.provider.nioHelpers.EelFiles", "readAllBytes"),
      )
    }
    if (fqn == "java.nio.file.Files.readString") {
      holder.registerProblem(
        methodPsi, "Works ineffectively with remote Eel", ProblemHighlightType.WARNING,
        ReplaceWithEelFunction("com.intellij.platform.eel.provider.nioHelpers.EelFiles", "readString"),
      )
    }
  }

  private class ReplaceWithEelFunction(private val receiver: String, private val method: String) : LocalQuickFix {
    override fun getFamilyName(): @IntentionFamilyName String =
      "Replace it with Eel API"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val methodCall = descriptor.psiElement.getUastParentOfType<UCallExpression>() ?: return
      val call = methodCall.uastParent as? UQualifiedReferenceExpression ?: methodCall
      val factory = call.getUastElementFactory(project) ?: return
      val context = call.sourcePsi ?: return

      val newCall = factory.createCallExpression(
        receiver = factory.createQualifiedReference(receiver, context),
        methodName = method,
        parameters = methodCall.valueArguments,
        expectedReturnType = null,
        kind = methodCall.kind,
        context = context,
      ) ?: return

      call.replace(newCall)
    }
  }
}