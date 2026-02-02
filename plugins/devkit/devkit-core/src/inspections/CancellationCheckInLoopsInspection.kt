// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.CommonClassNames.JAVA_LANG_ITERABLE
import com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_CONSUMER
import com.intellij.psi.CommonClassNames.JAVA_UTIL_ITERATOR
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_STREAM
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationCheckInLoopsFixProviders
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private val javaLoopMethods: CallMatcher = CallMatcher.anyOf(
  CallMatcher.instanceCall(JAVA_LANG_ITERABLE, "forEach").parameterTypes(JAVA_UTIL_FUNCTION_CONSUMER),
  CallMatcher.instanceCall(JAVA_UTIL_ITERATOR, "forEachRemaining").parameterTypes(JAVA_UTIL_FUNCTION_CONSUMER),
  CallMatcher.instanceCall(JAVA_UTIL_STREAM_STREAM, "forEach", "forEachOrdered").parameterTypes(JAVA_UTIL_FUNCTION_CONSUMER),
  CallMatcher.instanceCall(JAVA_UTIL_MAP, "forEach").parameterTypes("java.util.function.BiConsumer"),
  CallMatcher.staticCall(ContainerUtil::class.java.name, "process")
)

private val kotlinLoopMethods: CallMatcher = CallMatcher.anyOf(
  CallMatcher.staticCall("kotlin.collections.ArraysKt___ArraysKt", "forEach", "forEachIndexed"),
  CallMatcher.staticCall("kotlin.collections.CollectionsKt___CollectionsKt", "forEach", "forEachIndexed"),
  CallMatcher.staticCall("kotlin.collections.CollectionsKt__IteratorsKt", "forEach"),
  CallMatcher.staticCall("kotlin.collections.MapsKt___MapsKt", "forEach"),
  CallMatcher.staticCall("kotlin.sequences.SequencesKt___SequencesKt", "forEach", "forEachIndexed")
)

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

        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.isLoopMethodCall()) {
            val lambdaExpression = node.valueArguments.filterIsInstance<ULambdaExpression>().firstOrNull() ?: return true
            inspectLoopExpression(lambdaExpression, checkProvider, holder)
          }
          return true
        }
      },
      arrayOf(ULoopExpression::class.java, UCallExpression::class.java)
    )
  }

  /**
   * If the first expression in a loop is not another loop, finds the right cancellation check based on the context of a loop,
   * and if it's missing, registers the problem.
   */
  private fun inspectLoopExpression(loopOrLambdaExpression: UExpression,
                                    checkProvider: CancellationCheckProvider,
                                    holder: ProblemsHolder) {
    if (loopOrLambdaExpression !is ULoopExpression && loopOrLambdaExpression !is ULambdaExpression) return
    if (!shouldBeRunOn(loopOrLambdaExpression)) return

    val callContext = when (loopOrLambdaExpression) {
      is ULoopExpression -> loopOrLambdaExpression.sourcePsi
      is ULambdaExpression -> loopOrLambdaExpression.getParentOfType<UCallExpression>()?.sourcePsi
      else -> null
    } ?: return

    val firstExpressionInLoop = loopOrLambdaExpression.bodyExpressions.firstOrNull()?.let {
      if (it is UReturnExpression) it.returnExpression else it // fix for Kotlin implicit return in forEach functions
    }

    // Don't insert a check between nested loops if there is nothing in between
    if (firstExpressionInLoop?.isLoopExpressionOrLoopMethodCall() == true) return

    val cancellationCheckFqn = checkProvider.findCancellationCheckCall(callContext)
    val firstExpressionInLoopSourcePsi = firstExpressionInLoop?.sourcePsi
    if (firstExpressionInLoopSourcePsi != null && checkProvider.isCancellationCheckCall(firstExpressionInLoopSourcePsi,
                                                                                        cancellationCheckFqn)) return

    val anchor = getAnchor(loopOrLambdaExpression) ?: return
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

  private fun UExpression.isLoopExpressionOrLoopMethodCall(): Boolean {
    if (this is ULoopExpression) return true
    val call = when (this) {
      is UCallExpression -> this
      is UQualifiedReferenceExpression -> this.selector
      else -> return false
    } as UCallExpression
    return call.isLoopMethodCall()
  }

  private fun UCallExpression.isLoopMethodCall(): Boolean {
    return javaLoopMethods.uCallMatches(this) || kotlinLoopMethods.uCallMatches(this)
  }

  private val UExpression.bodyExpressions: List<UExpression>
    get() {
      val loopBody = when (this) {
        is ULoopExpression -> this.body
        is ULambdaExpression -> this.body
        else -> return emptyList()
      }
      return when (loopBody) {
        is UBlockExpression -> loopBody.expressions
        else -> listOf(loopBody)
      }
    }

  private fun getAnchor(loopOrLambdaExpression: UExpression): PsiElement? {
    return when (loopOrLambdaExpression) {
      is ULoopExpression -> loopOrLambdaExpression.sourcePsi?.firstChild
      is ULambdaExpression -> loopOrLambdaExpression.getParentOfType<UCallExpression>()?.methodIdentifier?.sourcePsi
      else -> null
    }
  }

}
