package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.lang.Language
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.panel
import org.jetbrains.completion.full.line.language.RedCodePolicy
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.components.RedCodePolicyRenderer
import org.jetbrains.completion.full.line.settings.ui.enableSubRowsIf

class CommonSettingsPanel(
  language: Language,
  flccEnabled: ComponentPredicate,
  langsEnabled: HashMap<String, ComponentPredicate>,
  reduced: Boolean = false,
) : ComplexPanel {
  private val settings = MLServerCompletionSettings.getInstance()

  val state = settings.getLangState(language)

  override val panel = panel {
    row {
      row {
        cell {
          label(message("fl.server.completion.ref.check"))
          comboBox(
            CollectionComboBoxModel(RedCodePolicy.values().toList()),
            state::redCodePolicy,
            RedCodePolicyRenderer()
          )
        }
      }
      row {
        cell {
          checkBox(message("fl.server.completion.enable.strings.walking"), state::stringsWalking)
          component(ContextHelpLabel.create(message("fl.server.completion.enable.strings.walking.help")))
        }
      }
      row {
        checkBox(message("fl.server.completion.only.full"), state::onlyFullLines)
      }
      if (!reduced) {
        row {
          checkBox(message("fl.server.completion.group.answers"), state::groupAnswers)
        }
        row {
          checkBox(message("fl.server.completion.score"), state::showScore)
        }
      }
    }.enableSubRowsIf(flccEnabled.and(langsEnabled.getValue(language.displayName)))
  }
}
