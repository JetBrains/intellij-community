// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProjectlessData {
  @JvmStatic
  fun forProject(project: Project?): EventPair<*>? {
    if (project == null || project.isDisposed) {
      return null
    }
    return EventFields.Projectless.with(WelcomeUtils.isWelcomeProject(project) || hasNoFrameOfItsOwn(project))
  }

  private fun hasNoFrameOfItsOwn(project: Project): Boolean =
    ProjectFrameCapabilitiesService.getInstanceSync().has(project, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)
}
