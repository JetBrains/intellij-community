// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

// extended externally
@ApiStatus.Internal
open class SettingsFrameFactory {
  companion object {
    @JvmStatic
    fun getInstance(): SettingsFrameFactory = service()
  }

  open fun show(project: Project, groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?) {
    SettingsFrame.getOrCreate(project, groups, configurable, filter, ::createFrame).show()
  }

  @ApiStatus.Internal
  @ApiStatus.OverrideOnly
  open fun createFrame(project: Project, groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?): SettingsFrame {
    return SettingsFrame(project, groups, configurable, filter)
  }
}
