// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.dsl.wrapWithLabel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

class GrazieNativeLanguageComponent(download: (Lang) -> Boolean) {
  private val link: LinkLabel<Any?> = LinkLabel<Any?>("", AllIcons.General.Warning).apply {
    setListener({ _, _ -> download(language) }, null)
  }

  private val combobox = ComboBox(Lang.sortedValues().toTypedArray()).apply {
    addItemListener { event ->
      val lang = (event.item as Lang)
      link.text = msg("grazie.ui.settings.languages.native.warning", lang.displayName)
      link.isVisible = lang.jLanguage == null
    }
  }

  var language: Lang
    get() = combobox.selectedItem as Lang
    set(value) {
      combobox.selectedItem = value
    }

  val component = panel(MigLayout(createLayoutConstraints(), AC())) {
    border = padding(JBUI.insetsBottom(10))
    add(wrapWithLabel(combobox, msg("grazie.ui.settings.languages.native.text")), CC().minWidth("220px").maxWidth("380px"))
    add(ContextHelpLabel.create(msg("grazie.ui.settings.languages.native.help")).apply {
      border = padding(JBUI.insetsLeft(5))
    }, CC().width("30px").alignX("left").wrap())
    add(link, CC().hideMode(3))
  }

  fun update() {
    link.isVisible = language.jLanguage == null
  }
}
