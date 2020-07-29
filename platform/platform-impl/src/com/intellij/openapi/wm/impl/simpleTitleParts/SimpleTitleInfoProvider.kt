// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.TitleInfoProvider

abstract class SimpleTitleInfoProvider(val option: TitleInfoOption) : TitleInfoProvider {

  init {
    option.listener = { updateSubscriptions() }
  }

  private var updateListener: HashSet<((provider: TitleInfoProvider) -> Unit)> = HashSet()

  override fun addUpdateListener(disposable: Disposable?, value: (provider: TitleInfoProvider) -> Unit) {
    updateListener.add(value)

    updateSubscriptions()
  }

  protected open fun updateSubscriptions() {
    updateValue()
  }

  protected open fun updateValue() {
    updateNotify()
  }

  protected open fun isEnabled(): Boolean {
    return option.isActive && updateListener.isNotEmpty()
  }

  private fun updateNotify() {
    updateListener.forEach { it(this) }
  }

  override val isActive: Boolean
    get() = isEnabled()

  override val borderlessSuffix: String = ""
  override val borderlessPrefix: String = " "
}