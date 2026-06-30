// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

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

internal fun installAgentThreadViewTerminalFileDropSupport(
  terminalComponent: JComponent,
  terminalTab: AgentThreadViewTerminalTab,
  parentDisposable: Disposable,
) {
  installAgentThreadViewFileDropSupport(
    targetComponent = terminalComponent,
    dropHandler = AgentThreadViewFileDropHandler { attachedObject -> handleAgentThreadViewTerminalFileDrop(attachedObject, terminalTab) },
    parentDisposable = parentDisposable,
  )
}

internal fun installAgentThreadViewContextFileDropSupport(
  contextComponent: JComponent,
  addContextItems: (List<AgentPromptContextItem>) -> Boolean,
  parentDisposable: Disposable,
) {
  installAgentThreadViewFileDropSupport(
    targetComponent = contextComponent,
    dropHandler = AgentThreadViewFileDropHandler { attachedObject -> handleAgentThreadViewContextFileDrop(attachedObject, addContextItems) },
    parentDisposable = parentDisposable,
  )
}

private fun installAgentThreadViewFileDropSupport(
  targetComponent: JComponent,
  dropHandler: AgentThreadViewFileDropHandler,
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

internal fun canHandleAgentThreadViewFileDrop(event: DnDEvent): Boolean {
  if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
    return false
  }

  event.setDropPossible(true)
  return true
}

internal fun handleAgentThreadViewTerminalFileDrop(attachedObject: Any?, terminalTab: AgentThreadViewTerminalTab): Boolean {
  val droppedPaths = getDroppedFilePaths(attachedObject).map(DroppedFilePath::path)
  if (droppedPaths.isEmpty()) {
    return false
  }

  terminalTab.sendText(formatDroppedFilePaths(droppedPaths), shouldExecute = false)
  terminalTab.preferredFocusableComponent.requestFocusInWindow()
  return true
}

internal fun handleAgentThreadViewContextFileDrop(
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

private class AgentThreadViewFileDropHandler(
  private val dropHandler: (Any?) -> Boolean,
) : DnDDropHandler.WithResult, DnDTargetChecker {
  override fun update(event: DnDEvent): Boolean {
    return !canHandleAgentThreadViewFileDrop(event)
  }

  override fun tryDrop(event: DnDEvent): Boolean {
    return dropHandler(event.attachedObject)
  }
}
