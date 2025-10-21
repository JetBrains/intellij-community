// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "RAW_RUN_BLOCKING")

package com.intellij.toolWindow

import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import javax.swing.Icon

fun testStripeButton(id: String, manager: ToolWindowManagerImpl, shouldBeVisible: Boolean) {
  val button = manager.getEntry(id)?.stripeButton
  if (!shouldBeVisible && button == null) {
    return
  }

  assertThat(button).isNotNull()
  assertThat(button!!.getComponent().isVisible).isEqualTo(shouldBeVisible)
}

private fun init(
  project: Project,
  isNewUi: Boolean,
  taskProducer: ((Project) -> List<RegisterToolWindowTaskData>)? = null,
  layoutCustomizer: ((DesktopLayout) -> Unit) = {},
): ToolWindowManagerImpl {
  val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
  val buttonManager = if (isNewUi) ToolWindowPaneNewButtonManager(paneId) else ToolWindowPaneOldButtonManager(paneId)
  val manager = object: ToolWindowManagerImpl(project, isNewUi = isNewUi, isEdtRequired = false, (project as ComponentManagerEx).getCoroutineScope()) {
    override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
  }
  Disposer.register(project, manager)

  val toolWindowLayoutManager = ToolWindowDefaultLayoutManager(isNewUi = isNewUi)
  toolWindowLayoutManager.noStateLoaded()

  manager.setLayoutOnInit(toolWindowLayoutManager.getLayoutCopy().also(layoutCustomizer))

  val tasks = if (taskProducer == null) runBlocking { computeToolWindowBeans(project) } else taskProducer(project)
  for (task in tasks) {
    manager.registerToolWindow(task, buttonManager)
  }
  return manager
}

object ToolWindowManagerTestHelper {
  fun available(isNewUi: Boolean, project: Project) {
    val manager = init(project, isNewUi)
    try {
      val toolWindow = manager.getToolWindow("Project")!!
      assertThat(manager.getEntry(toolWindow.id)!!.stripeButton).isNotNull()
      toolWindow.isAvailable = false
      assertThat(manager.getEntry(toolWindow.id)!!.stripeButton).isNull()
    }
    finally {
      Disposer.dispose(manager)
    }
  }

  fun showOnAvailable(isNewUi: Boolean, project: Project) {
    val id = "Notifications"
    val manager = init(
      project = project,
      isNewUi = isNewUi,
      taskProducer = {
        runBlocking { computeToolWindowBeans(project) }.map {
          if (it.id == id) {
            it.copy(shouldBeAvailable = false, contentFactory = object : ToolWindowFactory {
              override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
              }
            })
          }
          else {
            it
          }
        }
      },
      layoutCustomizer = {
        it.getInfo(id)!!.isVisible = true
      }
    )
    try {
      testButtonLayout(isNewUi, ToolWindowAnchor.BOTTOM)

      val entry = manager.getEntry(id)!!
      val toolWindow = entry.toolWindow
      assertThat(entry.stripeButton).isNull()
      toolWindow.isAvailable = true
      assertThat(entry.stripeButton).isNotNull()
      assertThat(entry.toolWindow.windowInfo.isVisible).isTrue()
    }
    finally {
      Disposer.dispose(manager)
    }
  }
}

suspend fun testDefaultLayout(isNewUi: Boolean, project: Project) {
  val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
  val buttonManager = if (isNewUi) ToolWindowPaneNewButtonManager(paneId) else ToolWindowPaneOldButtonManager(paneId)
  val manager = object: ToolWindowManagerImpl(project, isNewUi = isNewUi, isEdtRequired = false, (project as ComponentManagerEx).getCoroutineScope()) {
    override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
  }

  try {
    val toolWindowLayoutManager = ToolWindowDefaultLayoutManager(isNewUi = isNewUi)
    toolWindowLayoutManager.noStateLoaded()

    val layout = toolWindowLayoutManager.getLayoutCopy()
    val descriptor = layout.getInfo("Project")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor!!.isShowStripeButton).isTrue()

    assertThat(layout.getInfo("TODO")).isNull()

    manager.setLayoutOnInit(layout)
    assertThat(manager.getEntry("TODO")).isNull()

    val tasks = computeToolWindowBeans(project)
    for (task in tasks) {
      manager.registerToolWindow(task, buttonManager)
    }

    val todoInfo = manager.getEntry("TODO")!!.readOnlyWindowInfo
    // order allocation logic is the same for old and new ui, but by default not all tool windows are shown in a new UI,
    //  so the button for T O D O is not created by default, therefore, order is not set
    assertThat(todoInfo.order).let { if (isNewUi) it.isEqualTo(-1) else it.isNotEqualTo(-1) }
    assertThat(manager.getEntry("Project")!!.readOnlyWindowInfo.order).isNotEqualTo(-1)
  }
  finally {
    Disposer.dispose(manager)
  }
}

fun testButtonLayout(isNewUi: Boolean, anchor: ToolWindowAnchor) {
  fun info(order: Int): WindowInfoImpl {
    val result = WindowInfoImpl()
    result.order = order
    result.anchor = anchor
    return result
  }

  assertThat(
    sequenceOf(
      TestStripeButtonManager("Terminal", info(2)),
      TestStripeButtonManager("Version Control", info(0)),
      TestStripeButtonManager("Problems View", info(1)),
    )
      .sortedWith(AbstractDroppableStripe.createButtonLayoutComparator(isNewUi = isNewUi, anchor = anchor))
      .map { it.id }
      .toList()
  ).isEqualTo(listOf("Version Control", "Problems View", "Terminal")
                .let { if (isNewUi && anchor == ToolWindowAnchor.BOTTOM) it.asReversed() else it })
}

private class TestStripeButtonManager(override val id: String, override val windowDescriptor: WindowInfo) : StripeButtonManager {
  override val toolWindow: ToolWindowImpl
    get() = TODO("Not implemented")

  override fun updateState(toolWindow: ToolWindowImpl) {
    TODO("not implemented")
  }

  override fun updatePresentation() {
  }

  override fun updateIcon(icon: Icon?) {
  }

  override fun remove(anchor: ToolWindowAnchor, split: Boolean) {
  }

  override fun getComponent() = TODO("not implemented")

  override fun toString(): String {
    return "TestStripeButtonManager(id=$id, anchor=${windowDescriptor.anchor}, order=${windowDescriptor.order})"
  }
}