// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.buildPastedImageContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.readTransferableImage
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.util.Key
import java.awt.datatransfer.DataFlavor.imageFlavor

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
    val image = readTransferableImage(transferable, "Failed to get image data from clipboard") ?: return
    val contextItem = buildPastedImageContextItem(image)

    handler.onImagePasted(contextItem)
  }
}
