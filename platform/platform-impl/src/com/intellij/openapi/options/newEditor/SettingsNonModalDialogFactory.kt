// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Application service factory for [SettingsNonModalDialog].
 *
 * Override this service (with `overrides="true"`) to provide a custom subclass of
 * [SettingsNonModalDialog] — e.g. to fire remote-dev RD events on settings lifecycle hooks.
 *
 * @see SettingsDialogFactory for the modal-settings equivalent
 */
// extended externally
@ApiStatus.Internal
open class SettingsNonModalDialogFactory {
  companion object {
    @JvmStatic
    fun getInstance(): SettingsNonModalDialogFactory = service()
  }

  /**
   * Shows the single settings window of [project].
   *
   * [groups] is called only when a window must be created, so a repeat open of an already open
   * window builds no configurable tree. [configurable] receives the groups that the window shows,
   * because the tree selects a node by the [Configurable] instance.
   */
  open fun show(
    project: Project,
    groups: () -> List<ConfigurableGroup>,
    configurable: (List<ConfigurableGroup>) -> Configurable?,
    filter: String?,
  ) {
    SettingsNonModalDialog.getOrCreate(project, groups, configurable, filter, ::createDialog).show()
  }

  @ApiStatus.OverrideOnly
  protected open fun createDialog(
    project: Project,
    groups: List<ConfigurableGroup>,
    configurable: Configurable?,
    filter: String?,
  ): SettingsNonModalDialog = SettingsNonModalDialog(project, groups, configurable, filter)
}
