// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.DesktopLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

abstract class ToolWindowManagerEx : ToolWindowManager() {
  companion object {
    @JvmStatic
    fun getInstanceEx(project: Project): ToolWindowManagerEx = getInstance(project) as ToolWindowManagerEx
  }

  @get:Internal
  abstract val toolWindows: List<ToolWindow>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {{@link #registerToolWindow(RegisterToolWindowTask)}}")
  abstract fun initToolWindow(bean: ToolWindowEP)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  open fun addToolWindowManagerListener(listener: ToolWindowManagerListener) {
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  open fun addToolWindowManagerListener(listener: ToolWindowManagerListener, parentDisposable: Disposable) {
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  open fun removeToolWindowManagerListener(listener: ToolWindowManagerListener) {
  }

  /**
   * @return layout of tool windows.
   */
  abstract fun getLayout(): DesktopLayout

  abstract fun setLayout(newLayout: DesktopLayout)

  /**
   * Copied `layout` into internal layout and rearranges tool windows.
   */
  abstract var layoutToRestoreLater: DesktopLayout?

  abstract fun clearSideStack()

  open fun shouldUpdateToolWindowContent(toolWindow: ToolWindow): Boolean = toolWindow.isVisible

  abstract fun hideToolWindow(id: String, hideSide: Boolean)

  abstract fun getIdsOn(anchor: ToolWindowAnchor): List<String?>
}