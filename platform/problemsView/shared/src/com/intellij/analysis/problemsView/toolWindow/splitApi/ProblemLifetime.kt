// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProblemLifetime(val coroutineScope: CoroutineScope) {
  fun isActive(): Boolean = coroutineScope.isActive
  override fun toString(): String = coroutineScope.toString()
}
