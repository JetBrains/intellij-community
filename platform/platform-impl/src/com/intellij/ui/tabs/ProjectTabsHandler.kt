// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.wm.impl.IdeFrameImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Controls the project tabs of one IDE frame.
 * [ProjectTabsManager] creates it for the frame decorator.
 */
@ApiStatus.Internal
interface ProjectTabsHandler {
  /**
   * The frame decorator calls it when the frame gets its project.
   * Override it when the handler controls only one frame and keeps that frame itself.
   */
  fun setProject() {
  }

  /**
   * The frame decorator calls it when [frame] gets its project.
   * Override it when one handler controls many frames.
   * The default implementation calls [setProject] without the frame.
   */
  fun setProject(frame: IdeFrameImpl) {
    setProject()
  }

  /**
   * The frame decorator calls it when the application closes.
   */
  fun appClosing()
}
