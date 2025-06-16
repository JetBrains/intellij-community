// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.TimeoutSafeResult
import com.intellij.platform.debugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

internal class BackendXDebuggerNavigationApi : XDebuggerNavigationApi {
  override suspend fun navigateToXValue(xValueId: XValueId): TimeoutSafeResult<Boolean> {
    return navigate(xValueId) { xValue, navigatable ->
      xValue.computeSourcePosition(navigatable)
    }
  }

  override suspend fun navigateToXValueType(xValueId: XValueId): TimeoutSafeResult<Boolean> {
    return navigate(xValueId) { xValue, navigatable ->
      xValue.computeTypeSourcePosition(navigatable)
    }
  }

  private fun navigate(xValueId: XValueId, compute: (XValue, XNavigatable) -> Unit): TimeoutSafeResult<Boolean> {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return CompletableDeferred(false)
    val project = xValueModel.session.project
    val xValue = xValueModel.xValue
    val cs = xValueModel.session.coroutineScope
    return cs.async(Dispatchers.EDT) {
      val sourcePosition = computeSourcePositionWithTimeout { navigatable ->
        compute(xValue, navigatable)
      } ?: return@async false
      sourcePosition.createNavigatable(project).navigate(true)
      true
    }
  }
}

private val NAVIGATION_TIMEOUT = 10.seconds

/**
 * Computes [XSourcePosition] by [navigationRequest].
 * [XSourcePosition] has to be returned to the [XNavigatable] callback, otherwise the suspend function will be stuck.
 *
 * @see NAVIGATION_TIMEOUT
 */
@ApiStatus.Internal
suspend fun computeSourcePositionWithTimeout(navigationRequest: (XNavigatable) -> Unit): XSourcePosition? {
  val xSourceDeferred = CompletableDeferred<XSourcePosition?>()
  val navigatable = XNavigatable { sourcePosition ->
    xSourceDeferred.complete(sourcePosition)
  }
  navigationRequest(navigatable)

  return withTimeoutOrNull(NAVIGATION_TIMEOUT) {
    xSourceDeferred.await()
  }
}