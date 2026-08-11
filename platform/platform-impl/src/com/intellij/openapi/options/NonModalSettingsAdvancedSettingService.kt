// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import org.jetbrains.annotations.ApiStatus

/**
 * Service used solely for the `ide.ui.non.modal.settings.window` Advanced Setting visibility check.
 *
 * The Advanced Settings framework with `property="nonModalSettingsWindow"` calls
 * `isNonModalSettingsWindowVisible()` on this service instance to decide whether the setting
 * should appear in the Advanced Settings UI.
 *
 * Actual policy aggregation is delegated to [NonModalSettingsPolicy.isNonModalSettingsWindowSettingVisibleInAllPolicies].
 */
@ApiStatus.Internal
class NonModalSettingsAdvancedSettingService {
  fun isNonModalSettingsWindowVisible(): Boolean = NonModalSettingsPolicy.isNonModalSettingsWindowSettingVisibleInAllPolicies()
}
