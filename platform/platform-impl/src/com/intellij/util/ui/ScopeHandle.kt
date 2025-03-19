// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// to use in java code
internal class ScopeHandle {
  private var coroutineScope: CoroutineScope? = null

  var isDisposed: Boolean = true
    private set

  @RequiresEdt
  fun install(): CoroutineScope {
    assert(coroutineScope == null)
    coroutineScope = CoroutineScope(SupervisorJob() +
                                    Dispatchers.Default +
                                    ModalityState.defaultModalityState().asContextElement() +
                                    ClientId.coroutineContext())

    isDisposed = false
    return coroutineScope!!
  }

  fun uninstall() {
    isDisposed = true
    coroutineScope?.let {
      coroutineScope = null
      it.cancel()
    }
  }
}