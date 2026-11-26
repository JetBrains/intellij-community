// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Gives the order of the projects that one frame shows, for the UI that lists them.
 *
 * A frame shows its frame owner and the frame guests of the owner. Without a provider, the frame owner is first, and the frame
 * guests follow in the order they joined the frame. A product that keeps its own order of the projects gives it here.
 *
 * A provider whose order changes while the frame keeps its projects tells that through [ProjectFrameProjectOrderListener].
 */
@Internal
@Experimental
interface ProjectFrameProjectOrderProvider {
  /**
   * The projects of the frame of [frameOwner] in the order to list them, or `null` when this provider does not order the frame.
   * A project of the frame that the list lacks goes after the listed projects. The list can name a project that the frame
   * does not show.
   */
  fun getProjectOrder(frameOwner: Project): List<Project>?

  companion object {
    val EP_NAME: ExtensionPointName<ProjectFrameProjectOrderProvider> = ExtensionPointName("com.intellij.projectFrameProjectOrderProvider")
  }
}
