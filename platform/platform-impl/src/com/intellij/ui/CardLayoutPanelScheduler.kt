// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer
import java.util.function.Supplier

internal class CardLayoutPanelScheduler : Disposable {
  @Suppress("RAW_SCOPE_CREATION")
  private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())

  override fun dispose() {
    coroutineScope.cancel()
  }

  fun launch(modality: ModalityState, run: Runnable) {
    coroutineScope.launch(Dispatchers.UiWithModelAccess + modality.asContextElement()) {
      run.run()
    }
  }

  fun <T> computeAndLaunch(modality: ModalityState, compute: Supplier<T>, run: Consumer<T>) {
      coroutineScope.launch(Dispatchers.Default + modality.asContextElement()) {
        val result = compute.get()
        withContext(Dispatchers.UiWithModelAccess) {
          run.accept(result)
        }
      }
  }
}
