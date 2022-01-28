// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import org.assertj.core.api.Assertions.assertThat
import javax.swing.Icon

fun testStripeButton(id: String, manager: ToolWindowManagerImpl, shouldBeVisible: Boolean) {
  val button = manager.getEntry(id)?.stripeButton
   assertThat(button).isNotNull()
   assertThat(button!!.getComponent().isVisible).isEqualTo(shouldBeVisible)
}

fun testDefaultLayout(isNewUi: Boolean, project: Project) {
  val manager = ToolWindowManagerImpl(project, isNewUi = isNewUi)

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
    manager.registerToolWindow(task, if (isNewUi) ToolWindowPaneNewButtonManager() else ToolWindowPaneOldButtonManager())
  }

  val todoInfo = manager.getEntry("TODO")!!.readOnlyWindowInfo
  assertThat(todoInfo.isShowStripeButton).isEqualTo(!isNewUi)
  // order allocation logic is the same for old and new ui, but by default not all tool windows are shown in a new UI,
  // so, button for T O D O is not created by default, therefore order is not set
  assertThat(todoInfo.order).let { if (isNewUi) it.isEqualTo(-1) else it.isNotEqualTo(-1) }
  assertThat(manager.getEntry("Project")!!.readOnlyWindowInfo.order).isNotEqualTo(-1)
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
  override fun updateState(toolWindow: ToolWindowImpl) {
    TODO("not implemented")
  }

  override fun updatePresentation() {
  }

  override fun updateIcon(icon: Icon?) {
  }

  override fun remove() {
  }

  override fun getComponent() = TODO("not implemented")

  override fun toString(): String {
    return "TestStripeButtonManager(id=$id, anchor=${windowDescriptor.anchor}, order=${windowDescriptor.order})"
  }
}