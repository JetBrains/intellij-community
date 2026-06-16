// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.diagnostic.logger
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.extension

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
      LOG.warn("Failed to read image file: $path", e)
      null
    }
  }

  fun hasFileListFlavor(transferable: Transferable): Boolean {
    return FileCopyPasteUtil.isFileListFlavorAvailable(copyDataFlavors(transferable.transferDataFlavors))
  }

  fun hasImageFileInTransferable(transferable: Transferable): Boolean {
    return getImageFilesFromTransferable(transferable).isNotEmpty()
  }

  fun readImageFilesFromTransferable(transferable: Transferable): List<BufferedImage> {
    return getImageFilesFromTransferable(transferable).mapNotNull(::readImageFile)
  }

  fun getImageFilesFromAttachedObject(attachedObject: Any?): List<Path> {
    return FileCopyPasteUtil.getFileListFromAttachedObject(attachedObject)
      .map { file -> file.toPath() }
      .filter(::isReadableImageFile)
  }

  fun getImageFilesFromTransferable(transferable: Transferable): List<Path> {
    return FileCopyPasteUtil.getFileList(transferable).orEmpty()
      .map { file -> file.toPath() }
      .filter(::isReadableImageFile)
  }

  private fun rawImageToBufferedImage(rawImage: Any?): BufferedImage? {
    return when (rawImage) {
      is MultiResolutionImage -> rawImage.resolutionVariants.firstOrNull()?.toBufferedImage()
      is BufferedImage -> rawImage
      is Image -> rawImage.toBufferedImage()
      else -> null
    }
  }

  private fun isReadableImageFile(path: Path): Boolean {
    return Files.isRegularFile(path) && hasImageReaderForPath(path)
  }

  private fun hasImageReaderForPath(path: Path): Boolean {
    val extension = path.extension.takeIf { it.isNotBlank() } ?: return false
    return ImageIO.getImageReadersBySuffix(extension).hasNext()
  }

  private fun copyDataFlavors(transferFlavors: Array<out DataFlavor>): Array<DataFlavor> {
    return Array(transferFlavors.size) { index -> transferFlavors[index] }
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
