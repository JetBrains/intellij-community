// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.PropertiesComponent

class ToolwindowSidebarPositionProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  private val updaters = mutableListOf<Runnable>()

  override fun getId(): String = "toolwindow.sidebar.position"

  override fun getOptions(): Collection<OptionDescription> =
    listOf<OptionDescription>(object : BooleanOptionDescription(IdeBundle.message("label.toolwindow.sidebar.position"), null) {
      override fun isOptionEnabled() = PropertiesComponent.getInstance().isTrueValue(POSITION_RIGHT)
      override fun setOptionState(enabled: Boolean) {
        PropertiesComponent.getInstance().setValue(POSITION_RIGHT, enabled)
        updaters.forEach { it.run() }
      }
    })

  fun addUpdateListener(listener: Runnable) = updaters.add(listener)
  fun removeUpdateListener(listener: Runnable) = updaters.remove(listener)

  companion object {
    fun isRightPosition() = PropertiesComponent.getInstance().isTrueValue(POSITION_RIGHT)

    private const val POSITION_RIGHT: String = "false"
  }
}