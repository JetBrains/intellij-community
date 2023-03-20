package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.core.Language
import com.intellij.cce.workspace.Config
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EventDispatcher
import java.awt.event.ItemEvent
import javax.swing.JPanel

class LanguageConfigurable(private val dispatcher: EventDispatcher<SettingsListener>,
                           private val language2files: Map<String, Set<VirtualFile>>) : EvaluationConfigurable {
  private val languages = language2files.map { LanguageItem(it.key, it.value.size) }.sortedByDescending { it.count }
  private var lang = languages[0]
  fun language(): String {
    return lang.languageName
  }

  override fun createPanel(previousState: Config): JPanel {
    return panel {
      row(EvaluationPluginBundle.message("evaluation.settings.language.title")) {
        comboBox(languages)
          .bindItem({ lang }, { })
          .configure()
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun configure(builder: Config.Builder) {}

  private class LanguageItem(val languageName: String, val count: Int) {
    override fun toString(): String = "$languageName ($count)"
  }

  private fun Cell<ComboBox<LanguageItem>>.configure() {
    component.addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        lang = it.item as LanguageItem
        dispatcher.multicaster.languageChanged(Language.resolve(language()))
      }
    }
  }
}
