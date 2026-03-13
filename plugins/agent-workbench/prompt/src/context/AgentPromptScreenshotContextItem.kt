// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.openapi.application.PathManager
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

internal fun buildScreenshotContextItem(
  title: String,
  screenshot: BufferedImage,
  sourceId: String,
  source: String,
  tempFilePrefix: String,
): AgentPromptContextItem {
  val tempDir = PathManager.getTempDir()
  Files.createDirectories(tempDir)
  val screenshotFile = Files.createTempFile(tempDir, tempFilePrefix, ".png")
  @Suppress("SSBasedInspection") screenshotFile.toFile().deleteOnExit()
  Files.newOutputStream(screenshotFile).use { ImageIO.write(screenshot, "png", it) }
  val filePath = screenshotFile.toAbsolutePath().toString()

  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.SNIPPET,
    title = title,
    body = filePath,
    payload = AgentPromptPayload.obj(
      "type" to AgentPromptPayload.str("screenshot"),
      "filePath" to AgentPromptPayload.str(filePath),
      "width" to AgentPromptPayload.num(screenshot.width),
      "height" to AgentPromptPayload.num(screenshot.height),
    ),
    itemId = sourceId,
    source = source,
    truncation = AgentPromptContextTruncation.none(filePath.length),
  )
}
