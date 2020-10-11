// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.TitleInfoProvider

abstract class SimpleTitleInfoProvider(val option: TitleInfoOption) : TitleInfoProvider {
  init {
    option.listener = {
      updateNotify()
    }
  }

  private var updateListeners: MutableSet<((provider: TitleInfoProvider) -> Unit)> = HashSet()

  override val borderlessSuffix: String = ""
  override val borderlessPrefix: String = " "

  override fun addUpdateListener(project: Project, value: (provider: TitleInfoProvider) -> Unit) {
    updateListeners.add(value)
    updateNotify()
  }

  protected open fun isEnabled(): Boolean {
    return option.isActive && updateListeners.isNotEmpty()
  }

  private fun updateNotify() {
    updateListeners.forEach { it(this) }
    TitleInfoProvider.fireConfigurationChanged()
  }

  override fun isActive(project: Project) = isEnabled()
}