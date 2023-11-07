// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.actionSystem.DataKey

interface GHPRTimelineController {
  fun requestUpdate()

  companion object {
    val DATA_KEY: DataKey<GHPRTimelineController> = DataKey.create("GitHub.PullRequests.Timeline.Controller")
  }
}