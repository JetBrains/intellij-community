// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ObservableOperationTraceUtil")
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable

/**
 * Subscribes listener on operation start event that will be unsubscribed immediately before execution.
 * [parentDisposable] uses for early unsubscription when listener isn't called.
 * @see ObservableOperationTrace.beforeOperation(Int, () -> Unit, Disposable)
 */
fun ObservableOperationTrace.onceBeforeOperation(listener: () -> Unit, parentDisposable: Disposable) =
  beforeOperation(ttl = 1, listener, parentDisposable)

/**
 * Subscribes listener on operation finish event that will be unsubscribed immediately before execution.
 * @see onceBeforeOperation
 */
fun ObservableOperationTrace.onceAfterOperation(listener: () -> Unit, parentDisposable: Disposable) =
  afterOperation(ttl = 1, listener, parentDisposable)
