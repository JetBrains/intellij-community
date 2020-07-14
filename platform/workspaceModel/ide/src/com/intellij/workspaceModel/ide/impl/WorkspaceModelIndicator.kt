// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Debugger.Db_set_breakpoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeProjectLifecycleListener
import java.awt.event.MouseEvent
import javax.swing.Icon

@Suppress("HardCodedStringLiteral")
class WorkspaceModelIndicator : StatusBarWidgetFactory {
  override fun getId(): String = "WorkspaceModelIndicator"

  override fun getDisplayName(): String {
    return "Workspace Model"
  }

  override fun isAvailable(project: Project): Boolean {
    return Registry.`is`("ide.new.project.model.indicator")
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return object : StatusBarWidget.IconPresentation, StatusBarWidget {
      override fun ID(): String {
        return "Workspace Model widget indicator"
      }

      override fun install(statusBar: StatusBar) {
      }

      override fun dispose() {
      }

      override fun getTooltipText(): String? {
        return "Workspace model is ${if (LegacyBridgeProjectLifecycleListener.enabled(project)) "enabled" else "disabled"}"
      }

      override fun getIcon(): Icon? {
        return if (LegacyBridgeProjectLifecycleListener.enabled(project)) Db_set_breakpoint else AllIcons.Debugger.MuteBreakpoints
      }

      override fun getClickConsumer(): Consumer<MouseEvent>? {
        return null
      }

      override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
        return this
      }
    }
  }

  override fun disposeWidget(widget: StatusBarWidget) {
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
    return true
  }
}
