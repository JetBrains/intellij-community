// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ParallelOperationTraceUtil")
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operations.ParallelOperationTrace.Listener


private fun ParallelOperationTrace.subscribe(
  ttl: Int,
  listener: () -> Unit,
  wrap: (() -> Unit) -> Listener,
  parentDisposable: Disposable
) = subscribe(ttl, listener, wrap, this::subscribe, parentDisposable)

fun ParallelOperationTrace.onceBeforeOperation(listener: () -> Unit, parentDisposable: Disposable) =
  beforeOperation(ttl = 1, listener, parentDisposable)

fun ParallelOperationTrace.beforeOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable) =
  subscribe(ttl, listener, {
    object : Listener {
      override fun onOperationStart() = it()
    }
  }, parentDisposable)

fun ParallelOperationTrace.beforeOperation(listener: () -> Unit) =
  subscribe(object : Listener {
    override fun onOperationStart() = listener()
  })

fun ParallelOperationTrace.beforeOperation(listener: () -> Unit, parentDisposable: Disposable) =
  subscribe(object : Listener {
    override fun onOperationStart() = listener()
  }, parentDisposable)

fun ParallelOperationTrace.onceAfterOperation(listener: () -> Unit, parentDisposable: Disposable) =
  afterOperation(ttl = 1, listener, parentDisposable)

fun ParallelOperationTrace.afterOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable) =
  subscribe(ttl, listener, {
    object : Listener {
      override fun onOperationFinish() = it()
    }
  }, parentDisposable)

fun ParallelOperationTrace.afterOperation(listener: () -> Unit) =
  subscribe(object : Listener {
    override fun onOperationFinish() = listener()
  })

fun ParallelOperationTrace.afterOperation(listener: () -> Unit, parentDisposable: Disposable) =
  subscribe(object : Listener {
    override fun onOperationFinish() = listener()
  }, parentDisposable)
