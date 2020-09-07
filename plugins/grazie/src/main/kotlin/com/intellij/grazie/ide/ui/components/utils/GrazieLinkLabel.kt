// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.utils

import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import javax.swing.Icon
import javax.swing.JLabel

class GrazieLinkLabel(@NlsContexts.LinkLabel text : String, icon: Icon = AllIcons.Ide.External_link_arrow) {
  private val link = LinkLabel<Any?>(text, null)
  var listener: LinkListener<Any?>
    @Deprecated("Property can only be written", level = DeprecationLevel.ERROR)
    get() = throw NotImplementedError()
    set(value) {
      link.setListener(value, null)
    }

  val component = panel(HorizontalLayout(0)) {
    add(link)
    add(JLabel(icon))
  }
}
