// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

// extended externally
open class SettingsDialogFactory {
  companion object {
    @JvmStatic
    fun getInstance(): SettingsDialogFactory = service()
  }

  open fun create(project: Project?,
                  key: String,
                  configurable: Configurable,
                  showApplyButton: Boolean,
                  showResetButton: Boolean): DialogWrapper {
    return SettingsDialog(project, key, configurable, showApplyButton, showResetButton)
  }

  open fun create(parent: Component,
                  key: String,
                  configurable: Configurable,
                  showApplyButton: Boolean,
                  showResetButton: Boolean): DialogWrapper {
    return SettingsDialog(parent, key, configurable, showApplyButton, showResetButton)
  }

  open fun create(project: Project, groups: Array<ConfigurableGroup>, configurable: Configurable?, filter: String?): DialogWrapper {
    return create(project = project, groups = groups.asList(), configurable = configurable, filter = filter)
  }

  open fun create(project: Project, groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?): DialogWrapper {
    return create(project = project, groups = groups, configurable = configurable, filter = filter, isModal = true)
  }

  @ApiStatus.Internal
  open fun create(project: Project, groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?, isModal: Boolean): DialogWrapper {
    return SettingsDialog(project, null, groups, configurable, filter, isModal)
  }
}
