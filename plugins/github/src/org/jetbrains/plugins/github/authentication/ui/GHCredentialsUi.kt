// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.ui.util.Validator
import javax.swing.JComponent
import javax.swing.JPanel

internal abstract class GHCredentialsUi {
  abstract fun getPreferredFocusableComponent(): JComponent?
  abstract fun getValidator(): Validator
  abstract suspend fun login(server: GithubServerPath): Pair<String, String>
  abstract fun handleAcquireError(error: Throwable): ValidationInfo
  abstract fun setBusy(busy: Boolean)

  var footer: Panel.() -> Unit = { }

  fun getPanel(): JPanel =
    panel {
      centerPanel()
      footer()
    }.apply {
      // Border is required to have more space - otherwise there could be issues with focus ring.
      // `getRegularPanelInsets()` is used to simplify border calculation for dialogs where this panel is used.
      border = JBEmptyBorder(getRegularPanelInsets())
    }

  protected abstract fun Panel.centerPanel()
}