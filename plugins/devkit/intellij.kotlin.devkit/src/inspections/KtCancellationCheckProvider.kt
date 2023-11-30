// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.CancellationCheckProvider
import org.jetbrains.idea.devkit.kotlin.util.getContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val COROUTINE_CHECK_CANCELLED = "com.intellij.openapi.progress.checkCancelled"

internal class KtCancellationCheckProvider : CancellationCheckProvider {

  override fun findCancellationCheckCall(element: PsiElement): String {
    val context = getContext(element)
    return if (context.isSuspending()) COROUTINE_CHECK_CANCELLED else PROGRESS_MANAGER_CHECKED_CANCELED
  }

  override fun isCancellationCheckCall(element: PsiElement, cancellationCheckFqn: String): Boolean {
    val callExpression = when (element) {
      is KtCallExpression -> element
      is KtDotQualifiedExpression -> element.getChildOfType<KtCallExpression>() ?: return false
      else -> return false
    }

    analyze(callExpression) {
      val functionCalledSymbol = callExpression.resolveCall()?.singleFunctionCallOrNull()?.symbol ?: return false
      return functionCalledSymbol.callableIdIfNonLocal?.asSingleFqName() == FqName(cancellationCheckFqn)
    }
  }

}
