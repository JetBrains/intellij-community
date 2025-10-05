// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.xdebugger.XDebuggerBundle

internal class DebuggerGeneralConfigurable : BoundSearchableConfigurable("", "debugger.general") {


  override fun createPanel(): DialogPanel {
    val settings = XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings

    return panel {
      lateinit var showDebugWindow: Cell<JBCheckBox>
      row {
        showDebugWindow = checkBox(XDebuggerBundle.message("settings.show.window.label"))
          .bindSelected(settings::isShowDebuggerOnBreakpoint, settings::setShowDebuggerOnBreakpoint)
      }
      indent {
        row {
          checkBox(XDebuggerBundle.message("setting.focus.app.on.breakpoint.label"))
            .enabledIf(showDebugWindow.selected)
            .bindSelected(Registry.get("debugger.mayBringFrameToFrontOnBreakpoint").toBooleanProperty())
        }
      }
      if (Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume.supported").asBoolean()) {
        row {
          checkBox(XDebuggerBundle.message("setting.show.target.process.window.after.resume"))
            .gap(RightGap.SMALL)
            .bindSelected(Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume").toBooleanProperty())
          contextHelp(XDebuggerBundle.message("setting.show.target.process.window.after.resume.help.text"))
        }
      }
      row {
        checkBox(XDebuggerBundle.message("setting.hide.window.label"))
          .bindSelected(settings::isHideDebuggerOnProcessTermination, settings::setHideDebuggerOnProcessTermination)
      }
      row {
        checkBox(XDebuggerBundle.message("settings.scroll.to.center"))
          .bindSelected(settings::isScrollToCenter, settings::setScrollToCenter)
      }
      row {
        checkBox(XDebuggerBundle.message("settings.run.to.cursor.gesture"))
          .bindSelected(settings::isRunToCursorGestureEnabled, settings::setRunToCursorGestureEnabled)
      }

      buttonsGroup(XDebuggerBundle.message("settings.drag.to.remove.breakpoint")) {
        row {
          radioButton(XDebuggerBundle.message("settings.drag.to.remove.breakpoint.click"), false)
        }
        row {
          radioButton(XDebuggerBundle.message("settings.drag.to.remove.breakpoint.drag"), true)
        }
        row {
          checkBox(XDebuggerBundle.message("settings.confirm.breakpoint.removal"))
            .bindSelected(settings::isConfirmBreakpointRemoval, settings::setConfirmBreakpointRemoval)
        }
      }.bind(Registry.get("debugger.click.disable.breakpoints").toBooleanProperty())
    }
  }
}
