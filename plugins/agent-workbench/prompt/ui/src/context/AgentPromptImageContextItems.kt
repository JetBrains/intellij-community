// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.diagnostic.logger
import java.awt.Image
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val LOG = logger<AgentPromptImageContextItems>()

internal const val IMAGE_PASTE_SOURCE_ID = "manual.paste.image"
internal const val IMAGE_DROP_SOURCE_ID = "manual.drop.image"

private const val IMAGE_PASTE_SOURCE = "pastedImage"
private const val IMAGE_DROP_SOURCE = "droppedImage"

internal object AgentPromptImageContextItems {
  fun buildPastedImageContextItem(image: BufferedImage): AgentPromptContextItem {
    return AgentPromptScreenshotContextItem.buildScreenshotContextItem(
      title = AgentPromptBundle.message("manual.context.paste.image.title"),
      screenshot = image,
      sourceId = IMAGE_PASTE_SOURCE_ID,
      source = IMAGE_PASTE_SOURCE,
      tempFilePrefix = "pasted-image-",
    )
  }

  fun buildDroppedImageContextItem(image: BufferedImage): AgentPromptContextItem {
    return AgentPromptScreenshotContextItem.buildScreenshotContextItem(
      title = AgentPromptBundle.message("manual.context.paste.image.title"),
      screenshot = image,
      sourceId = IMAGE_DROP_SOURCE_ID,
      source = IMAGE_DROP_SOURCE,
      tempFilePrefix = "dropped-image-",
    )
  }

  fun readTransferableImage(transferable: Transferable, errorMessage: String): BufferedImage? {
    if (!transferable.isDataFlavorSupported(imageFlavor)) {
      return null
    }

    val rawImage = try {
      transferable.getTransferData(imageFlavor)
    }
    catch (_: UnsupportedFlavorException) {
      return null
    }
    catch (e: IOException) {
      LOG.error(errorMessage, e)
      return null
    }

    return rawImageToBufferedImage(rawImage)
  }

  fun readImageFile(path: Path): BufferedImage? {
    return try {
      Files.newInputStream(path).use(ImageIO::read)
    }
    catch (e: IOException) {
      LOG.warn("Failed to read dropped image file: $path", e)
      null
    }
  }

  private fun rawImageToBufferedImage(rawImage: Any?): BufferedImage? {
    return when (rawImage) {
      is MultiResolutionImage -> rawImage.resolutionVariants.firstOrNull()?.toBufferedImage()
      is BufferedImage -> rawImage
      is Image -> rawImage.toBufferedImage()
      else -> null
    }
  }

  @Suppress("UndesirableClassUsage")
  private fun Image.toBufferedImage(): BufferedImage = when (this) {
    is BufferedImage -> this
    else -> {
      val bufferedImage = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
      val g = bufferedImage.createGraphics()
      g.drawImage(this, 0, 0, null)
      g.dispose()
      bufferedImage
    }
  }
}
