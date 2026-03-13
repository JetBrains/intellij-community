// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context.screenCapture

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.AgentPromptScreenshotContextItem.buildScreenshotContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSourceBridge
import com.intellij.idea.AppMode
import com.intellij.openapi.project.Project
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage

private const val SCREEN_CAPTURE_SOURCE_ID = "manual.screen.capture"
private const val SCREEN_CAPTURE_SOURCE = "manualScreenCapture"

internal class AgentPromptScreenCaptureManualContextSource : AgentPromptManualContextSourceBridge {
  override val sourceId: String
    get() = SCREEN_CAPTURE_SOURCE_ID

  override val order: Int
    get() = 40

  override fun isAvailable(project: Project): Boolean {
    return !AppMode.isRemoteDevHost() && !GraphicsEnvironment.isHeadless()
  }

  override fun getDisplayName(): String {
    return AgentPromptBundle.message("manual.context.screen.display.name")
  }

  override fun showPicker(request: AgentPromptManualContextPickerRequest) {
    ScreenAreaPickerSession(
      anchorComponent = request.anchorComponent,
      onPicked = { screenshot -> request.onSelected(buildScreenCaptureContextItem(screenshot)) },
      onCanceled = {},
      onError = { request.onError(it) },
    ).start()
  }
}

internal fun buildScreenCaptureContextItem(screenshot: BufferedImage): AgentPromptContextItem {
  return buildScreenshotContextItem(
    title = AgentPromptBundle.message("manual.context.screen.title"),
    screenshot = screenshot,
    sourceId = SCREEN_CAPTURE_SOURCE_ID,
    source = SCREEN_CAPTURE_SOURCE,
    tempFilePrefix = "screen-capture-",
  )
}
