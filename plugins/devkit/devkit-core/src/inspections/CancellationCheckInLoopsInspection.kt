// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationCheckInLoopsFixProviders
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


internal class CancellationCheckInLoopsInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val checkProvider = CancellationCheckProviders.forLanguage(holder.file.language) ?: return PsiElementVisitor.EMPTY_VISITOR

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitForExpression(node: UForExpression): Boolean {
          inspectLoopExpression(node, checkProvider, holder)
          return true
        }

        override fun visitForEachExpression(node: UForEachExpression): Boolean {
          inspectLoopExpression(node, checkProvider, holder)
          return true
        }

        override fun visitWhileExpression(node: UWhileExpression): Boolean {
          inspectLoopExpression(node, checkProvider, holder)
          return true
        }

        override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
          inspectLoopExpression(node, checkProvider, holder)
          return true
        }
      },
      arrayOf(ULoopExpression::class.java)
    )
  }

  /**
   * If the first expression in a loop is not another loop, finds the right cancellation check based on the context of a loop,
   * and if it's missing, registers the problem.
   */
  private fun inspectLoopExpression(loopExpression: ULoopExpression,
                                    checkProvider: CancellationCheckProvider,
                                    holder: ProblemsHolder) {
    val sourcePsi = loopExpression.sourcePsi ?: return

    if (!shouldBeRunOn(loopExpression)) return

    val firstExpressionInLoop = loopExpression.bodyExpressions.firstOrNull()

    // Don't insert a check between nested loops if there is nothing in between
    if (firstExpressionInLoop is ULoopExpression) return

    val cancellationCheckFqn = checkProvider.findCancellationCheckCall(sourcePsi)
    val firstExpressionInLoopSourcePsi = firstExpressionInLoop?.sourcePsi
    if (firstExpressionInLoopSourcePsi != null && checkProvider.isCancellationCheckCall(firstExpressionInLoopSourcePsi,
                                                                                        cancellationCheckFqn)) return

    val anchor = sourcePsi.firstChild
    val fixProvider = CancellationCheckInLoopsFixProviders.forLanguage(holder.file.language) ?: return
    val fixes = fixProvider.getFixes(anchor, cancellationCheckFqn)
    holder.registerProblem(
      anchor,
      DevKitBundle.message("inspection.cancellation.check.in.loops.message", cancellationCheckFqn),
      *fixes.toTypedArray()
    )
  }

  /**
   * For now, insert a cancellation check in loops with [com.intellij.util.concurrency.annotations.RequiresReadLock].
   */
  private fun shouldBeRunOn(uElement: UElement): Boolean {
    val containingMethod = uElement.getParentOfType<UMethod>() ?: return false
    return AnnotationUtil.isAnnotated(containingMethod.javaPsi, RequiresReadLock::class.java.canonicalName, AnnotationUtil.CHECK_HIERARCHY)
  }

  private val ULoopExpression.bodyExpressions: List<UExpression>
    get() {
      return when (val loopBody = body) {
        is UBlockExpression -> loopBody.expressions
        else -> listOf(body)
      }
    }

}

