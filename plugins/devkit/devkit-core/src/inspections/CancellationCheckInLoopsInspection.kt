// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiUtil.getMemberQualifiedName
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private const val REQUIRES_READ_LOCK_FQN = "com.intellij.util.concurrency.annotations.RequiresReadLock"

class CancellationCheckInLoopsInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val checkProvider = CancellationCheckProviders.forLanguage(holder.file.language) ?: return PsiElementVisitor.EMPTY_VISITOR

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitForEachExpression(node: UForEachExpression): Boolean {
          inspectForEachExpression(node, checkProvider, holder)
          return true
        }
      },
      arrayOf(UForEachExpression::class.java)
    )
  }

  /**
   * If the first expression in a loop is not another loop, finds the right cancellation check based on the context of a loop,
   * and if it's missing, registers the problem.
   */
  private fun inspectForEachExpression(forEachExpression: UForEachExpression,
                                       checkProvider: CancellationCheckProvider,
                                       holder: ProblemsHolder) {
    val sourcePsi = forEachExpression.sourcePsi ?: return

    if (!shouldBeRunOn(forEachExpression)) return

    val firstExpressionInLoop = getFirstExpressionInLoop(forEachExpression)

    // Don't insert a check between nested loops if there is nothing in between
    if (firstExpressionInLoop is UForEachExpression) return

    val cancellationCheckFqn = checkProvider.findCancellationCheckFqn(sourcePsi)
    if (firstExpressionInLoop.isCancellationCheck(cancellationCheckFqn)) return

    val anchor = sourcePsi.firstChild
    holder.registerProblem(anchor, DevKitBundle.message("inspection.cancellation.check.in.loops.message", cancellationCheckFqn))
  }

  /**
   * For now, insert a cancellation check in loops with [com.intellij.util.concurrency.annotations.RequiresReadLock]
   */
  private fun shouldBeRunOn(uElement: UElement): Boolean {
    val containingMethod = uElement.getParentOfType<UMethod>() ?: return false
    val superMethods = containingMethod.javaPsi.findSuperMethods()
    return superMethods.plus(containingMethod).any { it.hasAnnotation(REQUIRES_READ_LOCK_FQN) }
  }

  private fun getFirstExpressionInLoop(loop: UForEachExpression): UExpression? {
    return when (val body = loop.body) {
      is UBlockExpression -> body.expressions.firstOrNull()
      else -> body
    }
  }

  private fun UExpression?.isCancellationCheck(cancellationCheckFqn: String): Boolean {
    val resolved = this?.tryResolve() as? PsiMember ?: return false
    return getMemberQualifiedName(resolved) == cancellationCheckFqn
  }

}
