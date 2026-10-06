// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class InlineBannerPanel : UISandboxPanel {

  override val title: String = "InlineBanner"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.inline.banner.group.all.default.statuses")) {
        val allStatuses = EditorNotificationPanel.Status.entries
        var index = 0
        while (index < allStatuses.size) {
          row {
            for (status in listOfNotNull(allStatuses[index++], allStatuses.getOrNull(index++))) {
              inlineBanner(DevkitUiDslBundle.message("sandbox.inline.banner.status.0", "EditorNotificationPanel.Status." + status.name),
                           status).applyToComponent {
                addButtonAction(DevkitUiDslBundle.message("sandbox.inline.banner.button")) {}
                addAction(DevkitUiDslBundle.message("sandbox.inline.banner.action")) {}
              }.resizableColumn()
            }
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.inline.banner.group.customizations")) {
        row {
          inlineBanner(DevkitUiDslBundle.message("sandbox.inline.banner.all.together"),
                       EditorNotificationPanel.Status.Info).applyToComponent {
            setGearAction(DevkitUiDslBundle.message("sandbox.inline.banner.gear.action")) {}
            setMenu(DevkitUiDslBundle.message("sandbox.inline.banner.menu"), createMenu())
            addButtonAction(DevkitUiDslBundle.message("sandbox.inline.banner.button")) {}
            addAction(DevkitUiDslBundle.message("sandbox.inline.banner.learn.more")) {}
            addAction(DevkitUiDslBundle.message("sandbox.inline.banner.additional.action")) {}
            addAction(DevkitUiDslBundle.message("sandbox.inline.banner.very.long.action")) {}
          }
        }
        row {
          inlineBanner(DevkitUiDslBundle.message("sandbox.inline.banner.no.close.button"),
                       EditorNotificationPanel.Status.Info).applyToComponent {
            setIcon(null)
            showCloseButton(false)
            for (i in 0..9) {
              addAction(DevkitUiDslBundle.message("sandbox.inline.banner.action.0", i)) {}
            }
          }
        }
      }
    }
  }

  private fun createMenu(): ActionGroup {
    val actions = (0..5).map { DumbAwareAction.create(DevkitUiDslBundle.message("sandbox.inline.banner.menu.item.0", it)) {} }
    return DefaultActionGroup(actions)
  }

  private fun Row.inlineBanner(text: @Nls String, status: EditorNotificationPanel.Status): Cell<InlineBanner> {
    return cell(InlineBanner(text, status))
      .align(AlignX.FILL)
      .applyToComponent { withPreferredWidth(300) }
  }
}
