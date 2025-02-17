// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.PopupHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.function.Supplier
import javax.swing.JComponent

@ApiStatus.ScheduledForRemoval
@Deprecated("Does nothing", ReplaceWith(""), DeprecationLevel.ERROR)
@Internal
class PopupMenuPreloader private constructor() : HierarchyListener {
  override fun hierarchyChanged(e: HierarchyEvent?) {
  }

  companion object {
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Does nothing", ReplaceWith(""), DeprecationLevel.ERROR)
    @JvmStatic
    fun install(component: JComponent,
                actionPlace: String,
                popupHandler: PopupHandler?,
                groupSupplier: Supplier<out ActionGroup>) {
    }
  }
}
