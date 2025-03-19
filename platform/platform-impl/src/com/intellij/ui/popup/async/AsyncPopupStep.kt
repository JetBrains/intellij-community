// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.async

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import org.jetbrains.concurrency.Promise

/**
 * Use this "mock" popup step when you need long processing (I/O, network activity etc.) to build real one.
 * The real example is a list of processes on remote host you can connect to with debugger.
 */
class AsyncPopupStep<T>(val promise: Promise<PopupStep<T>>) : PopupStep<T> {
  override fun getTitle() = null
  override fun onChosen(selectedValue: T, finalChoice: Boolean): PopupStep<T>? = fireUnsupportedException()
  override fun hasSubstep(selectedValue: T): Boolean = fireUnsupportedException()
  override fun canceled(): Unit = fireUnsupportedException()
  override fun isMnemonicsNavigationEnabled(): Boolean = fireUnsupportedException()
  override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<T>? = fireUnsupportedException()
  override fun isSpeedSearchEnabled(): Boolean = fireUnsupportedException()
  override fun getSpeedSearchFilter(): SpeedSearchFilter<T>? = fireUnsupportedException()
  override fun isAutoSelectionEnabled(): Boolean = fireUnsupportedException()
  override fun getFinalRunnable(): Runnable? = fireUnsupportedException()

  private fun <T> fireUnsupportedException(): T {
    throw UnsupportedOperationException("This operation is unsupported in async popup step")
  }
}
