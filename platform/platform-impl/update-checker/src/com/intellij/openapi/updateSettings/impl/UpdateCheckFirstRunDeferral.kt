// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.useOrLogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UpdateCheckFirstRunDeferral {
  suspend fun awaitLifted()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UpdateCheckFirstRunDeferral> =
      ExtensionPointName("com.intellij.updateCheckFirstRunDeferral")
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class UpdateCheckFirstRunDeferralRunner(private val scope: CoroutineScope) {
  fun runWhenLifted(action: Runnable) {
    scope.launch {
      for (extension in UpdateCheckFirstRunDeferral.EP_NAME.filterableLazySequence()) {
        extension.useOrLogError { it.awaitLifted() }
      }
      action.run()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): UpdateCheckFirstRunDeferralRunner = service()
  }
}
