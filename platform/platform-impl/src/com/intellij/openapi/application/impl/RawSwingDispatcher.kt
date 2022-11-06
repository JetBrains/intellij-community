// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext

/**
 * **Do not use it**. For low level Swing code only.
 */
internal object RawSwingDispatcher : CoroutineDispatcher() {

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    EventQueue.invokeLater(block)
  }

  override fun toString(): String {
    return "Swing"
  }
}
