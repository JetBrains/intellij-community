// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * The facts of the IJ Air block on the What's new page, for the Air plugin. The platform cannot see Air, so Air reports
 * them in its own FUS group.
 */
@ApiStatus.Internal
interface WhatsNewAirPromoListener {
  /** The page opened with the IJ Air block. It is sent before the page editor opens. */
  fun blockShown(project: Project, welcomeScreenProject: Boolean)

  /** The user clicked the button of the IJ Air block. It is sent before the button acts. */
  fun blockClicked(project: Project, welcomeScreenProject: Boolean)

  /** The tooltip on the Agent Sessions tool window showed after a project opened from the welcome screen. */
  fun tooltipShown(project: Project)

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<WhatsNewAirPromoListener> = Topic(WhatsNewAirPromoListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
