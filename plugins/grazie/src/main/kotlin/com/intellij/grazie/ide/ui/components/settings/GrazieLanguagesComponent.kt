// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.langlist.GrazieAddDeleteListPanel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.icons.AllIcons
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout

class GrazieLanguagesComponent(download: (Lang) -> Boolean, onLanguageAdded: (Lang) -> Unit, onLanguageRemoved: (Lang) -> Unit) {
  private val link: LinkLabel<Any?> = LinkLabel<Any?>(msg("grazie.languages.action"), AllIcons.General.Warning).apply {
    border = padding(JBUI.insetsTop(10))
    setListener({ _, _ -> GrazieConfig.get().missedLanguages.forEach { download(it) } }, null)
  }

  private val languages = GrazieAddDeleteListPanel(download, onLanguageAdded, { onLanguageRemoved(it); update() })

  val values
    get() = languages.listItems.toSet()

  val component = panel {
    add(languages, BorderLayout.CENTER)
    add(link, BorderLayout.SOUTH)
  }

  fun reset(langs: Collection<Lang>) = languages.reset(langs)

  fun update() {
    link.isVisible = GrazieConfig.get().hasMissedLanguages(withNative = false)
  }
}
