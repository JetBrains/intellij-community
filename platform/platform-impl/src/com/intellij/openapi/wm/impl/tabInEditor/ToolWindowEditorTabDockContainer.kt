// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.recordActionInvoked
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createDropTargetHighlightComponent
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createThumbnailDragImage
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.DockableContent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * A dock container that allows dragging a [ToolWindowEditorTabFile] from the editor
 * back into its original tool window.
 */
@ApiStatus.Internal
class ToolWindowEditorTabDockContainer private constructor(
  private val project: Project,
  private val toolWindowId: String,
  private val component: JComponent,
) : DockContainer {
  private val dropTargetHighlightComponent = createDropTargetHighlightComponent()
  private var currentHighlightParent: JComponent? = null
  private var currentDragImage: Image? = null

  override fun getAcceptArea(): RelativeRectangle = RelativeRectangle(component)

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint?): DockContainer.ContentResponse {
    val file = content.getToolWindowTabFile()
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    return if (file != null && toolWindow != null && project.service<ToolWindowEditorTabTransferController>()
        .canMoveContentToToolWindow(toolWindow, file)) {
      DockContainer.ContentResponse.ACCEPT_MOVE
    }
    else {
      DockContainer.ContentResponse.DENY
    }
  }

  override fun getContainerComponent(): JComponent = component

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    if (dropTarget == null) return

    val file = content.getToolWindowTabFile() ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return
    val targetDecorator = findTargetDecorator(dropTarget)

    recordMoveToToolWindowByDrag(file, toolWindow.id)
    project.service<ToolWindowEditorTabTransferController>().moveContentToToolWindow(toolWindow, file, targetDecorator)
  }

  override fun isEmpty(): Boolean = false

  override fun isDisposeWhenEmpty(): Boolean = false

  override fun processDropOver(content: DockableContent<*>, point: RelativePoint?): Image? {
    if (point == null) return null

    val targetDecorator = findTargetDecorator(point) ?: return null
    highlightDropArea(targetDecorator)
    return currentDragImage ?: createDragImage(content).also { currentDragImage = it }
  }

  override fun resetDropOver(content: DockableContent<*>) {
    clearDropAreaHighlight()
    currentDragImage = null
  }

  private fun findTargetDecorator(point: RelativePoint): InternalDecoratorImpl? {
    val dropPoint = point.getPoint(component)
    val deepestComponent = UIUtil.getDeepestComponentAt(component, dropPoint.x, dropPoint.y)
    return InternalDecoratorImpl.findNearestDecorator(deepestComponent) ?: component as? InternalDecoratorImpl
  }

  private fun highlightDropArea(targetDecorator: InternalDecoratorImpl) {
    val glassPane = targetDecorator.rootPane?.glassPane as? JComponent ?: return
    if (currentHighlightParent !== glassPane) {
      clearDropAreaHighlight()
      glassPane.add(dropTargetHighlightComponent)
      currentHighlightParent = glassPane
    }

    val dropArea = SwingUtilities.convertRectangle(targetDecorator.parent, targetDecorator.bounds, glassPane)
    dropTargetHighlightComponent.bounds = dropArea
    dropTargetHighlightComponent.isVisible = true
    glassPane.revalidate()
    glassPane.repaint(dropArea)
  }

  private fun clearDropAreaHighlight() {
    val parent = currentHighlightParent
    if (parent != null) {
      val bounds = Rectangle(dropTargetHighlightComponent.bounds)
      parent.remove(dropTargetHighlightComponent)
      parent.revalidate()
      if (!bounds.isEmpty) {
        parent.repaint(bounds)
      }
      currentHighlightParent = null
    }
    dropTargetHighlightComponent.bounds = Rectangle()
  }

  private fun createDragImage(content: DockableContent<*>): Image {
    val presentation = content.presentation
    val label = JLabel(presentation.text, presentation.icon, SwingConstants.LEADING).apply {
      border = JBUI.Borders.empty(4, 8)
      size = preferredSize
    }
    return createThumbnailDragImage(label, -1)
  }

  private fun DockableContent<*>.getToolWindowTabFile(): ToolWindowEditorTabFile? =
    getKey() as? ToolWindowEditorTabFile

  private fun recordMoveToToolWindowByDrag(file: ToolWindowEditorTabFile, targetToolWindowId: String) {
    val action = ActionManager.getInstance().getAction("MoveToolWindowTabFromEditorToToolWindowAction") ?: return
    val targetToolWindow = ToolWindowManager.getInstance(project).getToolWindow(targetToolWindowId) ?: return
    val dataContext = SimpleDataContext.builder()
      .setParent(DataContext.EMPTY_CONTEXT)
      .add(CommonDataKeys.VIRTUAL_FILE, file)
      .add(PlatformDataKeys.TOOL_WINDOW, targetToolWindow)
      .build()
    val event = AnActionEvent.createEvent(
      action,
      dataContext,
      null,
      ActionPlaces.EDITOR_TAB,
      ActionUiKind.NONE,
      MouseEvent(component, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false, MouseEvent.BUTTON1),
    )
    recordActionInvoked(project, action, event) { }
  }

  companion object {
    private const val INSTALLED_PROPERTY = "ToolWindowDockContainer.installed"

    /*
     * Registers the editor dock container for the given tool window.
     */
    @JvmStatic
    fun install(project: Project, toolWindowId: String, decorator: InternalDecorator) {
      if (decorator.getClientProperty(INSTALLED_PROPERTY) == true) return
      decorator.putClientProperty(INSTALLED_PROPERTY, true)

      decorator.launchOnShow("ToolWindowDockContainer") {
        val container = ToolWindowEditorTabDockContainer(project, toolWindowId, decorator)
        val disposable = Disposer.newDisposable("ToolWindowDockContainer")
        DockManager.getInstance(project).register(container, disposable)

        try {
          awaitCancellation()
        }
        finally {
          // Dispose on EDT as well because registering/unregistering the dock container is not thread-safe.
          Disposer.dispose(disposable)
        }
      }
    }
  }
}
