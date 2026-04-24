// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal object AgentPromptScreenshotContextItem {
  private const val SCREENSHOT_CONTEXT_TYPE = "screenshot"
  private const val SCREENSHOT_CONTEXT_TYPE_FIELD = "type"
  private const val SCREENSHOT_CONTEXT_FILE_PATH_FIELD = "filePath"

  private val LOG = logger<AgentPromptScreenshotContextItem>()

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
        SCREENSHOT_CONTEXT_TYPE_FIELD to AgentPromptPayload.str(SCREENSHOT_CONTEXT_TYPE),
        SCREENSHOT_CONTEXT_FILE_PATH_FIELD to AgentPromptPayload.str(filePath),
        "width" to AgentPromptPayload.num(screenshot.width),
        "height" to AgentPromptPayload.num(screenshot.height),
      ),
      itemId = sourceId,
      source = source,
      truncation = AgentPromptContextTruncation.none(filePath.length),
    )
  }

  internal fun deleteScreenshotContextFileIfPresent(item: AgentPromptContextItem) {
    val payload = item.payload.objOrNull() ?: return
    if (payload.string(SCREENSHOT_CONTEXT_TYPE_FIELD) != SCREENSHOT_CONTEXT_TYPE) {
      return
    }

    val filePath = payload.string(SCREENSHOT_CONTEXT_FILE_PATH_FIELD) ?: return
    runCatching {
      Files.deleteIfExists(Path.of(filePath))
    }.onFailure { error ->
      LOG.warn("Failed to delete screenshot context file: $filePath", error)
    }
  }
}
