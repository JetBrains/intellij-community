// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.util.Key
import java.awt.Image
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.IOException

private val LOG = logger<AgentPromptImagePasteProvider>()

internal const val IMAGE_PASTE_SOURCE_ID = "manual.paste.image"
private const val IMAGE_PASTE_SOURCE = "pastedImage"

internal fun interface AgentPromptImagePasteHandler {
  fun onImagePasted(item: AgentPromptContextItem)
}

internal val AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY: Key<AgentPromptImagePasteHandler> =
  Key.create("AgentPromptImagePasteHandler")

internal class AgentPromptImagePasteProvider : PasteProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isPastePossible(dataContext: DataContext): Boolean {
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return false
    if (editor.getUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY) == null) return false
    val transferable = dataContext.getData(PasteAction.TRANSFERABLE_PROVIDER)?.produce() ?: return false
    return transferable.isDataFlavorSupported(imageFlavor)
  }

  override fun isPasteEnabled(dataContext: DataContext): Boolean = isPastePossible(dataContext)

  override fun performPaste(dataContext: DataContext) {
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
    val handler = editor.getUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY) ?: return
    val transferable = dataContext.getData(PasteAction.TRANSFERABLE_PROVIDER)?.produce() ?: return
    val image = getImageContents(transferable) ?: return

    val contextItem = AgentPromptScreenshotContextItem.buildScreenshotContextItem(
      title = AgentPromptBundle.message("manual.context.paste.image.title"),
      screenshot = image,
      sourceId = IMAGE_PASTE_SOURCE_ID,
      source = IMAGE_PASTE_SOURCE,
      tempFilePrefix = "pasted-image-",
    )

    handler.onImagePasted(contextItem)
  }

  private fun getImageContents(transferable: Transferable): BufferedImage? {
    val rawImage = try {
      transferable.getTransferData(imageFlavor)
    }
    catch (_: UnsupportedFlavorException) {
      return null
    }
    catch (e: IOException) {
      LOG.error("Failed to get image data from clipboard", e)
      return null
    }

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
