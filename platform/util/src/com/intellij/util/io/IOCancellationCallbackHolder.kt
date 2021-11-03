// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.util.*

internal object IOCancellationCallbackHolder {
  val usedIoCallback by lazy { loadSingleCallback() }

  private fun loadSingleCallback(): IOCancellationCallback {
    val serviceLoader = ServiceLoader.load(IOCancellationCallback::class.java, IOCancellationCallback::class.java.classLoader)
    val allCallbacks = serviceLoader.toList()
    if (allCallbacks.size > 1) {
      throw IllegalStateException("Few io cancellation callbacks are registered: ${allCallbacks.map { it.javaClass.name }}")
    }
    return allCallbacks.firstOrNull() ?: object : IOCancellationCallback {
      override fun checkCancelled() = Unit

      override fun interactWithUI() = Unit
    }
  }

  @JvmStatic
  fun checkCancelled() = usedIoCallback.checkCancelled()

  @JvmStatic
  fun interactWithUI() = usedIoCallback.interactWithUI()
}