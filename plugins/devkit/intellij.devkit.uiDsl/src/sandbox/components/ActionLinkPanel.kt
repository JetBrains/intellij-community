// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ActionLinkPanel : UISandboxPanel {

  override val title: String = "ActionLink"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.action.link.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.link.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.action.link.label.default")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.action.link.text.open.settings")) {})
        }
        row(DevkitUiDslBundle.message("sandbox.action.link.label.with.icon")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.action.link.text.add.configuration")) {}.apply {
            setIcon(AllIcons.General.Add)
          })
        }
        row(DevkitUiDslBundle.message("sandbox.action.link.label.disabled")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.action.link.text.reset.to.defaults")) {}.apply {
            autoHideOnDisable = false
            isEnabled = false
          }).comment(DevkitUiDslBundle.message("sandbox.action.link.comment.auto.hide"))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.action.link.group.ui.dsl")) {
        row {
          link(DevkitUiDslBundle.message("sandbox.action.link.text.learn.shortcuts")) {}
        }
      }
    }
  }
}
