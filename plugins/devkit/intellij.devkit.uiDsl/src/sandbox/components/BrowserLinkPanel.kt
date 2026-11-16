// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val SDK_DOCS_URL: @NonNls String = "https://plugins.jetbrains.com/docs/intellij/"

internal class BrowserLinkPanel : UISandboxPanel {

  override val title: String = "BrowserLink"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.browser.link.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.browser.link.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.browser.link.label.default")) {
          cell(BrowserLink(DevkitUiDslBundle.message("sandbox.browser.link.text.sdk.docs"), SDK_DOCS_URL))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.browser.link.group.ui.dsl")) {
        row {
          browserLink(DevkitUiDslBundle.message("sandbox.browser.link.text.sdk.docs"), SDK_DOCS_URL)
        }
      }
    }
  }
}
