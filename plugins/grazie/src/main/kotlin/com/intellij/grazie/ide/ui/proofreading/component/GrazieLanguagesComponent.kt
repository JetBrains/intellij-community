// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.proofreading.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.grazie.ide.ui.proofreading.component.list.GrazieLanguagesList
import com.intellij.grazie.jlanguage.Lang
import com.intellij.icons.AllIcons
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JPanel

class GrazieLanguagesComponent(download: suspend (Lang) -> Unit) : GrazieUIComponent {
  private val languages = GrazieLanguagesList(download) {
    updateLinkToDownloadMissingLanguages()
  }

  private val link: LinkLabel<Any?> = LinkLabel<Any?>(msg("grazie.notification.missing-languages.action"), AllIcons.General.Warning).configure {
    border = padding(JBUI.insetsTop(10))
    setListener({ _, _ -> GrazieConfig.get().missedLanguages.forEach { GrazieScope.coroutineScope().launch { download(it) } } }, null)
  }

  override val component: JPanel = panel {
    add(languages, BorderLayout.CENTER)
    add(link, BorderLayout.SOUTH)
  }

  fun updateLinkToDownloadMissingLanguages() {
    link.isVisible = GrazieConfig.get().hasMissedLanguages()
  }

  override fun isModified(state: GrazieConfig.State): Boolean {
    return languages.isModified(state)
  }

  override fun reset(state: GrazieConfig.State) {
    updateLinkToDownloadMissingLanguages()
    languages.reset(state)
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State {
    return languages.apply(state)
  }
}
