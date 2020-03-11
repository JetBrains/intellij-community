package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.border
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.proofreading.component.GrazieLanguagesComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.ide.DataManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import javax.swing.event.HyperlinkEvent

class ProofreadSettingsPanel : ConfigurableUi<GrazieConfig> {
  private val languages = GrazieLanguagesComponent(::download)

  private fun download(lang: Lang): Boolean {
    val isSucceed = GrazieRemote.download(lang, guessCurrentProject(languages.component))
    if (isSucceed) languages.updateLinkToDownloadMissingLanguages()
    return isSucceed
  }

  override fun reset(settings: GrazieConfig) = languages.reset(settings.state)

  override fun isModified(settings: GrazieConfig) = languages.isModified(settings.state)

  override fun apply(settings: GrazieConfig) {
    GrazieConfig.update { state ->
      languages.apply(state)
    }
  }

  override fun getComponent() = panel(MigLayout(createLayoutConstraints())) {
    panel(MigLayout(createLayoutConstraints()), constraint = CC().growX().wrap()) {
      border = border(msg("grazie.settings.proofreading.languages.text"), false, JBUI.insetsBottom(10), false)
      add(languages.component, CC().width("350px").height("150px"))
    }

    languages.reset(GrazieConfig.get())

    val link = HyperlinkLabel(msg("grazie.settings.proofreading.link-to-inspection"))
    link.addHyperlinkListener { e: HyperlinkEvent ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        Settings.KEY.getData(DataManager.getInstance().getDataContext(this))?.let { settings ->
          settings.find(ErrorsConfigurable::class.java)?.let {
            settings.select(it).doWhenDone {
              it.selectInspectionGroup(arrayOf(msg("grazie.group.name")))
            }
          }
        }
      }
    }
    add(link, CC())
  }
}
