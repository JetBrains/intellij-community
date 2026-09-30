// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.runtime.product.ProductMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Runs the first update check when the IDE is ready for it.
 *
 * In the Light mode, the check waits until the product advances to the frontend mode.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class UpdateCheckFirstRunDeferralRunner(private val scope: CoroutineScope) {
  fun runWhenLifted(action: Runnable) {
    scope.launch {
      if (IdeProductMode.isLight) {
        ProductLoadingStrategy.strategy.currentModeIdFlow.first { it == ProductMode.FRONTEND.id }
      }
      action.run()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): UpdateCheckFirstRunDeferralRunner = service()
  }
}
