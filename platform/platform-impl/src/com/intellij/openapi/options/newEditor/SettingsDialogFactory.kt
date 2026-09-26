// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Component

/**
 * Low-level factory for the **modal** Settings dialog (`SettingsDialog`, a `DialogWrapper` with
 * `IdeModalityType.IDE`).
 *
 * ### Prefer [com.intellij.openapi.options.ShowSettingsUtil] in plugin code
 *
 * In nearly all cases, plugins should open Settings via
 * [com.intellij.openapi.options.ShowSettingsUtil] or
 * [com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog] (the `@JvmStatic` helper that
 * takes a configurable id + filter). Those entry points are responsible for:
 *
 * - deciding whether the invocation should produce a **modal** dialog (this factory) or be routed
 *   into the **non-modal** Settings window (`SettingsNonModalDialog`), depending on user
 *   preferences and the current modality;
 * - reusing the already-open non-modal Settings window instead of stacking a second one on top of
 *   it (`SettingsNonModalDialog.getOrCreate`);
 * - dispatching to the right host in remote-dev / Code With Me (`BackendShowSettingsUtil`).
 *
 * Calling this factory directly **bypasses all three**: it unconditionally constructs a modal
 * `SettingsDialog`. When the user has the main non-modal Settings already open, a direct factory
 * call pops a second, modal Settings window on top of it. Don't do this from action code,
 * quick-fixes, notifications, status-bar widgets, etc.
 *
 * ### When direct use is appropriate
 *
 * - **Overriding this service** to inject a host-specific dialog (e.g. `RadSettingsDialogFactory`,
 *   `ThinClientSettingsDialogFactory`, `RiderSettingsDialogFactory`). The platform itself calls
 *   `create(...)` from `ShowSettingsUtilImpl.doShow` once it has decided modal is appropriate;
 *   service overrides plug in there.
 * - Very specific in-IDE flows that require the full `DialogWrapper` lifecycle (`showAndGet()`,
 *   custom dimension key, `setOnDeactivationAction`, etc.) for a *single-page* configurable.
 *   These are equivalent to [com.intellij.openapi.options.ShowSettingsUtil.editConfigurable];
 *   prefer that API where possible.
 *
 * @see com.intellij.openapi.options.ShowSettingsUtil
 * @see com.intellij.ide.actions.ShowSettingsUtilImpl
 * @see SettingsNonModalDialogFactory
 */
// extended externally
open class SettingsDialogFactory {
  companion object {
    @JvmStatic
    fun getInstance(): SettingsDialogFactory = service()
  }

  /**
   * Creates a **modal** single-page Settings dialog for the given [configurable].
   *
   * Prefer [com.intellij.openapi.options.ShowSettingsUtil.editConfigurable] over calling this
   * directly — it goes through the public API and is consistent with the rest of the codebase.
   */
  open fun create(project: Project?,
                  key: String,
                  configurable: Configurable,
                  showApplyButton: Boolean,
                  showResetButton: Boolean): DialogWrapper {
    return SettingsDialog(project, key, configurable, showApplyButton, showResetButton)
  }

  /**
   * Creates a **modal** single-page Settings dialog for the given [configurable], parented to
   * [parent].
   *
   * Prefer [com.intellij.openapi.options.ShowSettingsUtil.editConfigurable] (the `Component`
   * overload) over calling this directly.
   */
  open fun create(parent: Component,
                  key: String,
                  configurable: Configurable,
                  showApplyButton: Boolean,
                  showResetButton: Boolean): DialogWrapper {
    return SettingsDialog(parent, key, configurable, showApplyButton, showResetButton)
  }

  /**
   * Creates a **modal** full-tree Settings dialog, optionally preselecting [configurable] and
   * applying a [filter].
   *
   * **Do not call this from plugin code to open Settings on a specific page.** Use
   * [com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog]`(project, idToSelect, filter)`
   * instead — that path honors the non-modal Settings window and reuses an already-open one
   * (`SettingsNonModalDialog.getOrCreate`) instead of stacking a duplicate modal dialog on top of
   * it.
   */
  open fun create(project: Project, groups: Array<ConfigurableGroup>, configurable: Configurable?, filter: String?): DialogWrapper {
    return create(project = project, groups = groups.asList(), configurable = configurable, filter = filter)
  }

  /**
   * Creates a **modal** full-tree Settings dialog, optionally preselecting [configurable] and
   * applying a [filter].
   *
   * **Do not call this from plugin code to open Settings on a specific page.** Use
   * [com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog]`(project, idToSelect, filter)`
   * instead — that path honors the non-modal Settings window and reuses an already-open one
   * (`SettingsNonModalDialog.getOrCreate`) instead of stacking a duplicate modal dialog on top of
   * it.
   */
  open fun create(project: Project, groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?): DialogWrapper {
    return SettingsDialog(project, null, groups, configurable, filter)
  }
}
