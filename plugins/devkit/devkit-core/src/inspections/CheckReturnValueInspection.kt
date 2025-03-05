// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.getReturnType
import com.intellij.lang.LanguageExpressionTypes
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiTypes
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class CheckReturnValueInspection : DevKitUastInspectionBase() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          ProgressManager.checkCanceled()

          val method = node.resolve() ?: return true

          if (!method.annotations.any { it.qualifiedName == "org.jetbrains.annotations.CheckReturnValue" }) {
            return true
          }

          val elementToReport = findProblematicExpression(node, node)

          if (elementToReport != null) {
            holder.registerProblem(
              elementToReport.sourcePsi!!,
              DevKitBundle.message("inspection.message.return.value.must.be.checked", this@CheckReturnValueInspection.id),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
          }
          return true
        }
      },
      arrayOf(UCallExpression::class.java),
    )

  private tailrec fun findProblematicExpression(expression: UElement?, initialExpression: UElement): UElement? {
    ProgressManager.checkCanceled()
    return when (val uastParent = expression?.uastParent) {
      null -> null

      is UArrayAccessExpression -> initialExpression

      is UBinaryExpression -> initialExpression

      is UBlockExpression ->
        if (uastParent.expressions.lastOrNull()?.sourcePsi === expression.sourcePsi)
          findProblematicExpression(uastParent, initialExpression)
        else
          initialExpression

      is UCallExpression -> {
        val resolvedMethod = uastParent.tryResolveNamed().toUElement() as? UMethod ?: return null
        val resolvedClass = resolvedMethod.getContainingUClass()?.qualifiedName ?: return null
        val methodQualifiedName = "$resolvedClass.${resolvedMethod.name}"
        if (methodQualifiedName in FUNCTIONS_THAT_RETURN_AS_IS)
          findProblematicExpression(uastParent, initialExpression)
        else null
      }

      is UExpressionList -> initialExpression

      is UIfExpression -> null

      is ULambdaExpression ->
        if (uastParent.getReturnType() == PsiTypes.voidType()) initialExpression
        else findProblematicExpression(uastParent, initialExpression)

      is ULoopExpression -> null

      is UMethod ->
        if (uastParent.returnType == PsiTypes.voidType()) initialExpression
        else null

      is UParenthesizedExpression ->
        findProblematicExpression(uastParent, initialExpression)

      is UPolyadicExpression -> null

      is UQualifiedReferenceExpression ->
        if (uastParent.selector.sourcePsi === expression.sourcePsi)
          findProblematicExpression(uastParent, initialExpression)
        else null

      is UReturnExpression -> {
        val jump = uastParent.jumpTarget ?: return null
        val dumbService = DumbService.getInstance(expression.sourcePsiElement!!.project)
        val checkers = dumbService.filterByDumbAwareness(LanguageExpressionTypes.INSTANCE.allForLanguage(expression.lang))

        if (
          checkers.any { checker ->
            // Dealing with no dependency on kotlin-compiler-common.
            checker.getInformationHint(jump.sourcePsi!!).removeSuffix("</html>").endsWith("-&gt; Unit")
          }
        ) {
          return initialExpression
        }

        return findProblematicExpression(uastParent, initialExpression)
      }

      is USwitchExpression -> null

      is UThrowExpression -> null

      is UTryExpression -> null

      is UUnaryExpression -> null

      is UVariable ->
        if (uastParent.type == PsiTypes.voidType()) initialExpression
        else null

      else -> null
    }
  }
}

// TODO It must be something smarter than a hard-coded list.
private val FUNCTIONS_THAT_RETURN_AS_IS: Set<String> = setOf(
  "kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking",
  "kotlinx.coroutines.BuildersKt__BuildersKt.withContext",
  "kotlin.io.CloseableKt__CloseableKt.use",
)
