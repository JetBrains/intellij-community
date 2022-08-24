package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.lang.Language
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.components.LoadingComponent
import org.jetbrains.completion.full.line.settings.ui.components.languageCheckBox
import org.jetbrains.completion.full.line.settings.ui.components.loadingStatus
import org.jetbrains.completion.full.line.settings.ui.components.pingButton
import org.jetbrains.completion.full.line.settings.ui.fullRow

class LanguageCloudModelPanel(
  languages: Collection<Language>,
  private val flccEnabled: ComponentPredicate,
  authTokenTextField: JBPasswordField? = null,
) : ComplexPanel {
  private val rows = languages.map {
    LanguageRow(
      it,
      authTokenTextField,
      MLServerCompletionSettings.getInstance().state.langStates.keys.maxByOrNull { it.length },
    )
  }

  override val panel = panel {
    rows.forEach {
      it.row(this).enableIf(flccEnabled)
    }

  }.apply {
    name = ModelType.Cloud.name
  }

  private class LanguageRow(val language: Language, val authTokenTextField: JBPasswordField?, biggestLang: String?) : ComplexRow {
    private val checkBox = languageCheckBox(language, biggestLang)
    private val loadingIcon = LoadingComponent()

    override fun row(builder: LayoutBuilder) = builder.fullRow {
      component(checkBox)
        .withSelectedBinding(MLServerCompletionSettings.getInstance().getLangState(language)::enabled.toBinding())
      component(pingButton(language, loadingIcon, authTokenTextField))
        .enableIf(checkBox.selected)
      loadingStatus(loadingIcon)
        .forEach { it.enableIf(checkBox.selected) }
    }
  }
}
