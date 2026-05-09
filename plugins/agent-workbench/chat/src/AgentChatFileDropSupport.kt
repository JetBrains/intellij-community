// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.prompt.context.ManualPathSelectionEntry
import com.intellij.agent.workbench.prompt.context.buildManualPathsContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.DnDTargetChecker
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.isDirectory

internal fun installAgentChatTerminalFileDropSupport(
  terminalComponent: JComponent,
  terminalTab: AgentChatTerminalTab,
  parentDisposable: Disposable,
) {
  installAgentChatFileDropSupport(
    targetComponent = terminalComponent,
    dropHandler = AgentChatFileDropHandler { attachedObject -> handleAgentChatTerminalFileDrop(attachedObject, terminalTab) },
    parentDisposable = parentDisposable,
  )
}

internal fun installAgentChatContextFileDropSupport(
  contextComponent: JComponent,
  addContextItems: (List<AgentPromptContextItem>) -> Boolean,
  parentDisposable: Disposable,
) {
  installAgentChatFileDropSupport(
    targetComponent = contextComponent,
    dropHandler = AgentChatFileDropHandler { attachedObject -> handleAgentChatContextFileDrop(attachedObject, addContextItems) },
    parentDisposable = parentDisposable,
  )
}

private fun installAgentChatFileDropSupport(
  targetComponent: JComponent,
  dropHandler: AgentChatFileDropHandler,
  parentDisposable: Disposable,
) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  DnDSupport.createBuilder(targetComponent)
    .setDisposableParent(parentDisposable)
    .setDropHandlerWithResult(dropHandler)
    .setTargetChecker(dropHandler)
    .enableAsNativeTarget()
    .disableAsSource()
    .install()
}

internal fun canHandleAgentChatFileDrop(event: DnDEvent): Boolean {
  if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
    return false
  }

  event.setDropPossible(true)
  return true
}

internal fun handleAgentChatTerminalFileDrop(attachedObject: Any?, terminalTab: AgentChatTerminalTab): Boolean {
  val droppedPaths = getDroppedFilePaths(attachedObject).map(DroppedFilePath::path)
  if (droppedPaths.isEmpty()) {
    return false
  }

  terminalTab.sendText(formatDroppedFilePaths(droppedPaths), shouldExecute = false)
  terminalTab.preferredFocusableComponent.requestFocusInWindow()
  return true
}

internal fun handleAgentChatContextFileDrop(
  attachedObject: Any?,
  addContextItems: (List<AgentPromptContextItem>) -> Boolean,
): Boolean {
  val droppedFiles = getDroppedFilePaths(attachedObject).takeIf { it.isNotEmpty() } ?: return false
  return addContextItems(listOf(buildDroppedFilesContextItem(droppedFiles)))
}

internal fun formatDroppedFilePaths(paths: List<Path>): String {
  return ParametersListUtil.join(paths.map(Path::toString))
}

private fun getDroppedFilePaths(attachedObject: Any?): List<DroppedFilePath> {
  return FileCopyPasteUtil.getFileListFromAttachedObject(attachedObject).map { file ->
    val path = file.toPath()
    DroppedFilePath(path = path, isDirectory = path.isDirectory())
  }
}

private fun buildDroppedFilesContextItem(files: List<DroppedFilePath>): AgentPromptContextItem {
  return buildManualPathsContextItem(
    files.map { file ->
      ManualPathSelectionEntry(path = file.path.toString(), isDirectory = file.isDirectory)
    },
  )
}

private data class DroppedFilePath(
  @JvmField val path: Path,
  @JvmField val isDirectory: Boolean,
)

private class AgentChatFileDropHandler(
  private val dropHandler: (Any?) -> Boolean,
) : DnDDropHandler.WithResult, DnDTargetChecker {
  override fun update(event: DnDEvent): Boolean {
    return !canHandleAgentChatFileDrop(event)
  }

  override fun tryDrop(event: DnDEvent): Boolean {
    return dropHandler(event.attachedObject)
  }
}
