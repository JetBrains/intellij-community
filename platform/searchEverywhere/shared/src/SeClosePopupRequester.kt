// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Allows a [SeItemsProvider.itemSelected] implementation that defers an action via
 * `ApplicationManager.invokeLater` (e.g. `GotoActionAction.openOptionOrPerformAction`)
 * to close the SE popup synchronously inside the same EDT event as `processSelectedItem`.
 *
 * Without this, the queued action runs before the popup-close coroutine continuation
 * propagates back up to `SePopupContentPane.elementsSelected`, leaving the popup visible
 * after a new window opens. On Wayland this is user-visible because
 * `setCancelOnWindowDeactivation` is disabled there (Wayland focus events are not atomic).
 *
 * Must be invoked from inside the same `withContext(Dispatchers.EDT)` block that calls
 * `processSelectedItem` — otherwise the timing guarantee is lost.
 */
@ApiStatus.Internal
class SeClosePopupRequester(val close: () -> Unit) : AbstractCoroutineContextElement(SeClosePopupRequester) {
  companion object Key : CoroutineContext.Key<SeClosePopupRequester>
}
