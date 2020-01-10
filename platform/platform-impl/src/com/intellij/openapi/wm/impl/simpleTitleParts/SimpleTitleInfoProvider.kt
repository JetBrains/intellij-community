// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.TitleInfoProvider

abstract class SimpleTitleInfoProvider(defaultSubscription: TitleInfoSubscription,
                                       borderlessSubscription: TitleInfoSubscription) : TitleInfoProvider {
  private var subscription: TitleInfoSubscription = if (IdeFrameDecorator.isCustomDecorationActive()) borderlessSubscription else defaultSubscription

  init {
    subscription.listener = { updateSubscriptions() }
  }

  private var updateListener: HashSet<(() -> Unit)> = HashSet()

  override fun addUpdateListener(value: () -> Unit, disposable: Disposable?) {
    updateListener.add(value)

    updateSubscriptions()
  }

  protected open fun updateSubscriptions() {
    updateValue()
  }

  protected open fun updateValue() {
    borderlessTitlePart.active = isActive
    borderlessTitlePart.longText = value

    updateNotify()
  }

  protected open fun isEnabled(): Boolean {
    return subscription.isActive && updateListener.isNotEmpty()
  }

  private fun updateNotify() {
    updateListener.forEach { it() }
  }

  override val isActive: Boolean
    get() = isEnabled()
}