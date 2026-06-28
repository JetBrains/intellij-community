// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.buildDroppedImageContextItem
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.getImageFilesFromAttachedObject
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.getImageFilesFromTransferable
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.readImageFile
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImageContextItems.readTransferableImage
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.DnDTargetChecker
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.util.disposeOnCompletion
import kotlinx.coroutines.CoroutineScope
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent

internal fun interface AgentPromptImageDropHandler {
  fun onImagesDropped(items: List<AgentPromptContextItem>): Boolean
}

private const val IMAGE_DROP_TARGET_INSTALLED_PROPERTY = "AgentPromptImageDropTargetInstalled"

internal fun installAgentPromptImageDropSupport(
  targetComponent: JComponent,
  dropHandler: AgentPromptImageDropHandler,
  parentDisposable: Disposable,
) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  val dndHandler = AgentPromptImageDndHandler(dropHandler)
  installAgentPromptImageDropTarget(targetComponent, dndHandler, parentDisposable)
}

internal fun installAgentPromptDialogImageDropSupport(
  rootComponent: JComponent,
  dropHandler: AgentPromptImageDropHandler,
  coroutineScope: CoroutineScope,
) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  installAgentPromptDialogImageDropSupport(
    rootComponent = rootComponent,
    dropHandler = dropHandler,
    parentDisposable = disposableFromScope(coroutineScope),
  )
}

internal fun installAgentPromptDialogImageDropSupport(
  rootComponent: JComponent,
  dropHandler: AgentPromptImageDropHandler,
  parentDisposable: Disposable,
) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  AgentPromptDialogImageDropInstaller(
    dndHandler = AgentPromptImageDndHandler(dropHandler),
    parentDisposable = parentDisposable,
  ).install(rootComponent)
}

private fun disposableFromScope(coroutineScope: CoroutineScope): Disposable {
  return Disposer.newDisposable("AgentPromptDialogImageDropSupport").also { disposable ->
    disposable.disposeOnCompletion(coroutineScope)
  }
}

internal fun installAgentPromptEditorImageDropSupport(
  editor: Editor,
  dropHandler: AgentPromptImageDropHandler,
) {
  @Suppress("UsePropertyAccessSyntax")
  (editor as? EditorImpl)?.setDropHandler(AgentPromptImageEditorDropHandler(dropHandler))
}

private fun installAgentPromptImageDropTarget(
  targetComponent: JComponent,
  dndHandler: AgentPromptImageDndHandler,
  parentDisposable: Disposable,
) {
  if (targetComponent.getClientProperty(IMAGE_DROP_TARGET_INSTALLED_PROPERTY) == true) {
    return
  }

  targetComponent.putClientProperty(IMAGE_DROP_TARGET_INSTALLED_PROPERTY, true)
  Disposer.register(parentDisposable) {
    targetComponent.putClientProperty(IMAGE_DROP_TARGET_INSTALLED_PROPERTY, null)
  }
  DnDSupport.createBuilder(targetComponent)
    .setDisposableParent(parentDisposable)
    .setDropHandlerWithResult(dndHandler)
    .setTargetChecker(dndHandler)
    .enableAsNativeTarget()
    .disableAsSource()
    .install()
}

private class AgentPromptDialogImageDropInstaller(
  private val dndHandler: AgentPromptImageDndHandler,
  private val parentDisposable: Disposable,
) {
  private val listenedContainers = HashSet<Container>()
  private val containerListener = object : ContainerAdapter() {
    override fun componentAdded(event: ContainerEvent) {
      install(event.child)
    }
  }

  fun install(component: Component) {
    if (component is JComponent && shouldInstallNativeImageDropTarget(component)) {
      installAgentPromptImageDropTarget(component, dndHandler, parentDisposable)
    }
    if (component is Container) {
      installContainerListener(component)
      component.components.forEach(::install)
    }
  }

  private fun installContainerListener(container: Container) {
    if (!listenedContainers.add(container)) {
      return
    }

    container.addContainerListener(containerListener)
    Disposer.register(parentDisposable) {
      container.removeContainerListener(containerListener)
      listenedContainers.remove(container)
    }
  }

  private fun shouldInstallNativeImageDropTarget(component: JComponent): Boolean {
    return !isInsideEditorTextField(component)
  }

  private fun isInsideEditorTextField(component: Component): Boolean {
    var current: Component? = component
    while (current != null) {
      if (current is EditorTextField) {
        return true
      }
      current = current.parent
    }
    return false
  }
}

internal fun canHandleAgentPromptImageDrop(event: DnDEvent): Boolean {
  if (event.isDataFlavorSupported(imageFlavor)) {
    event.setDropPossible(true)
    return true
  }

  if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
    return false
  }

  if (getImageFilesFromAttachedObject(event.attachedObject).isEmpty()) {
    return false
  }

  event.setDropPossible(true)
  return true
}

internal fun canHandleAgentPromptImageTransfer(transferFlavors: Array<out DataFlavor>): Boolean {
  return transferFlavors.any { flavor -> flavor == imageFlavor }
         || FileCopyPasteUtil.isFileListFlavorAvailable(copyDataFlavors(transferFlavors))
}

internal fun buildDroppedImageContextItems(event: DnDEvent): List<AgentPromptContextItem> {
  return buildDroppedImageContextItems(resolveDropTransferable(event))
}

internal fun buildDroppedImageContextItems(transferable: Transferable): List<AgentPromptContextItem> {
  val transferableImage = readTransferableImage(transferable, "Failed to get image data from drag-and-drop event")
  if (transferableImage != null) {
    return listOf(buildDroppedImageContextItem(transferableImage))
  }

  return getImageFilesFromTransferable(transferable).mapNotNull { file ->
    readImageFile(file)?.let(::buildDroppedImageContextItem)
  }
}

private fun resolveDropTransferable(event: DnDEvent): Transferable {
  return when (val attachedObject = event.attachedObject) {
    is DnDNativeTarget.EventInfo -> attachedObject.transferable
    else -> event
  }
}

private fun copyDataFlavors(transferFlavors: Array<out DataFlavor>): Array<DataFlavor> {
  return Array(transferFlavors.size) { index -> transferFlavors[index] }
}

internal class AgentPromptImageEditorDropHandler(
  private val dropHandler: AgentPromptImageDropHandler,
) : EditorDropHandler {
  override fun canHandleDrop(transferFlavors: Array<out DataFlavor>): Boolean {
    return canHandleAgentPromptImageTransfer(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindowCandidate: EditorWindow?) {
    val items = buildDroppedImageContextItems(t)
    if (items.isNotEmpty()) {
      dropHandler.onImagesDropped(items)
    }
  }
}

private class AgentPromptImageDndHandler(
  private val dropHandler: AgentPromptImageDropHandler,
) : DnDDropHandler.WithResult, DnDTargetChecker {
  override fun update(event: DnDEvent): Boolean {
    return !canHandleAgentPromptImageDrop(event)
  }

  override fun tryDrop(event: DnDEvent): Boolean {
    val items = buildDroppedImageContextItems(event)
    return items.isNotEmpty() && dropHandler.onImagesDropped(items)
  }
}
