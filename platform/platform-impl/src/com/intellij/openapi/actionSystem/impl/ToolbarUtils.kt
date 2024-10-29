// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.ui.ComponentUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import javax.swing.JComponent

@ApiStatus.Internal
object ToolbarUtils {

  fun createImmediatelyUpdatedToolbar(
    group: ActionGroup,
    place: String,
    targetComponent: JComponent,
    horizontal: Boolean = true,
    onUpdated: (ActionToolbar) -> Unit
  ): ActionToolbar {
    val toolbar = object : ActionToolbarImpl(place, group, horizontal) {
      override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
        val firstTime = forced && !hasVisibleActions()
        super.actionsUpdated(forced, newVisibleActions)
        if (firstTime) {
          ComponentUtil.markAsShowing(this, false)
          onUpdated.invoke(this)
        }
      }
    }
    toolbar.targetComponent = targetComponent
    toolbar.putClientProperty(ActionToolbarImpl.SUPPRESS_FAST_TRACK, true)
    toolbar.setReservePlaceAutoPopupIcon(false)
    ComponentUtil.markAsShowing(toolbar, true)
    toolbar.updateActionsImmediately(true)
    return toolbar
  }

  fun createTargetComponent(editor: Editor, dataProvider: UiDataProvider): JComponent {
    return MyComponent(editor.contentComponent, dataProvider)
  }

  fun createTargetComponent(component: JComponent, dataProvider: UiDataProvider): JComponent {
    return MyComponent(component, dataProvider)
  }

  private class MyComponent(val base: JComponent,
                            val provider: UiDataProvider
  ) : JComponent(), UiDataProvider {
    override fun getParent(): Container = base
    override fun isShowing() = true
    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, provider)
    }
  }
}