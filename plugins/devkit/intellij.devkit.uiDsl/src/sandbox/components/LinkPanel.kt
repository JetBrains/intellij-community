// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val SDK_DOCS_URL: @NonNls String = "https://plugins.jetbrains.com/docs/intellij/"

internal class LinkPanel : UISandboxPanel {

  override val title: String = "Link"

  override val description: String
    get() = DevkitUiDslBundle.message("sandbox.link.description")

  override val usageNotes: List<String>
    get() = listOf(DevkitUiDslBundle.message("sandbox.link.usage.text"))

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.link.group.action.link")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.link.usage.action.link"))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.default")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.link.text.open.settings")) {})
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.with.icon")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.link.text.add.configuration")) {}.apply {
            setIcon(AllIcons.General.Add)
          })
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.disabled")) {
          cell(ActionLink(DevkitUiDslBundle.message("sandbox.link.text.reset.to.defaults")) {}.apply {
            autoHideOnDisable = false
            isEnabled = false
          }).comment(DevkitUiDslBundle.message("sandbox.link.comment.auto.hide"))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.ui.dsl")) {
          @NlsSafe val code = "link(text) { }"
          link(DevkitUiDslBundle.message("sandbox.link.text.learn.shortcuts")) {}
            .comment(code)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.link.group.drop.down.link")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.link.usage.drop.down.link"))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.sort.by")) {
          val items = listOf(
            DevkitUiDslBundle.message("sandbox.link.item.name"),
            DevkitUiDslBundle.message("sandbox.link.item.date"),
            DevkitUiDslBundle.message("sandbox.link.item.size"),
          )
          cell(DropDownLink(items[0], items) {})
        }
      }

      group(DevkitUiDslBundle.message("sandbox.link.group.browser.link")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.link.usage.browser.link"))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.default")) {
          cell(BrowserLink(DevkitUiDslBundle.message("sandbox.link.text.sdk.docs"), SDK_DOCS_URL))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.ui.dsl")) {
          @NlsSafe val code = "browserLink(text, url)"
          browserLink(DevkitUiDslBundle.message("sandbox.link.text.sdk.docs"), SDK_DOCS_URL)
            .comment(code)
        }
      }
    }
  }
}
