// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.TitleInfoProvider

abstract class SimpleTitleInfoProvider(protected val option: TitleInfoOption) : TitleInfoProvider {
  init {
    option.listener = {
      updateNotify()
    }
  }

  private var updateListeners = mutableSetOf<((provider: TitleInfoProvider) -> Unit)>()

  override val borderlessSuffix: String = ""
  override val borderlessPrefix: String = " "

  final override fun addUpdateListener(project: Project, disp: Disposable, value: (provider: TitleInfoProvider) -> Unit) {
    addSubscription(project, disp, value)
    updateNotify()
  }

  protected open fun addSubscription(project: Project, disp: Disposable, value: (provider: TitleInfoProvider) -> Unit) {
    updateListeners.add(value)
    Disposer.register(disp, Disposable {
      updateListeners.remove(value)
    })
  }

  protected open fun isEnabled(): Boolean {
    return option.isActive && updateListeners.isNotEmpty()
  }

  protected fun updateNotify() {
    updateListeners.forEach { it(this) }
    TitleInfoProvider.fireConfigurationChanged()
  }

  override fun isActive(project: Project) = isEnabled()
}