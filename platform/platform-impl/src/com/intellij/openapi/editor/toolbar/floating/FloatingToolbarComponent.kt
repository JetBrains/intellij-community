// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

interface FloatingToolbarComponent {

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated("Floating toolbar component should have auto-update")
  fun update() {
  }

  fun scheduleHide()

  fun scheduleShow()

  companion object {
    @Deprecated("Use FloatingToolbarProviderUtil instead")
    val KEY = DataKey.create<FloatingToolbarComponent>("com.intellij.openapi.editor.toolbar.floating.Component")
  }
}