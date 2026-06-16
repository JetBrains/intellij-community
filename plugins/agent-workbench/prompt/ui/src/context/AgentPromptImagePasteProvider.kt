// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.buildPastedImageContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.hasFileListFlavor
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.hasImageFileInTransferable
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.readImageFilesFromTransferable
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.readTransferableImage
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.util.Key
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable

internal fun interface AgentPromptImagePasteHandler {
  fun onImagesPasted(items: List<AgentPromptContextItem>)
}

internal val AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY: Key<AgentPromptImagePasteHandler> =
  Key.create("AgentPromptImagePasteHandler")

internal class AgentPromptImagePasteProvider : PasteProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isPastePossible(dataContext: DataContext): Boolean {
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return false
    if (editor.getUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY) == null) return false
    val transferable = dataContext.getData(PasteAction.TRANSFERABLE_PROVIDER)?.produce() ?: return false
    return canPasteImage(transferable)
  }

  override fun isPasteEnabled(dataContext: DataContext): Boolean = isPastePossible(dataContext)

  override fun performPaste(dataContext: DataContext) {
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
    val handler = editor.getUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY) ?: return
    val transferable = dataContext.getData(PasteAction.TRANSFERABLE_PROVIDER)?.produce() ?: return
    val contextItems = buildPastedImageContextItems(transferable)
    if (contextItems.isEmpty()) return

    handler.onImagesPasted(contextItems)
  }

  private fun canPasteImage(transferable: Transferable): Boolean {
    if (hasFileListFlavor(transferable)) {
      return hasImageFileInTransferable(transferable)
    }

    return transferable.isDataFlavorSupported(imageFlavor)
  }

  private fun buildPastedImageContextItems(transferable: Transferable): List<AgentPromptContextItem> {
    if (hasFileListFlavor(transferable)) {
      return readImageFilesFromTransferable(transferable).map(::buildPastedImageContextItem)
    }

    val image = readTransferableImage(transferable, "Failed to get image data from clipboard") ?: return emptyList()
    return listOf(buildPastedImageContextItem(image))
  }
}
