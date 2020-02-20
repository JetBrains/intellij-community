// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.wrapWithComment
import com.intellij.ui.components.JBCheckBox

class GrazieCommitComponent {
  private val checkbox = JBCheckBox(msg("grazie.ui.settings.vcs.enable.text"))
  var isCommitIntegrationEnabled: Boolean
    get() = checkbox.isSelected
    set(value) {
      checkbox.isSelected = value
    }

  val component = wrapWithComment(checkbox, msg("grazie.ui.settings.vcs.enable.note"))
}
