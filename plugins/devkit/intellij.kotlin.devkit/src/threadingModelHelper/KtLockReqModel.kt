// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import org.jetbrains.idea.devkit.threadingModelHelper.LockRequirement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction

private fun KtCallExpression.resolveToFunctionSymbol() {
  TODO()
}

data class KtMethodCall(val function: KtFunction)

data class KtExecutionPath(
  val path: List<KtMethodCall>,
  val requirement: LockRequirement,
)

data class KtAnalysisResult(val function: KtFunction, val paths: Set<KtExecutionPath>)