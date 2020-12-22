// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@Deprecated("Redundant, all defaults described in FloatingToolbarProviderBean")
class DefaultFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  override val priority = 0

  override val autoHideable = true

  companion object {
    @NonNls
    const val ACTION_GROUP = "EditorContextBarMenu"
  }
}