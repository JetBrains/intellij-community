package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.local.ModelSchema
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.services.managers.missedLanguage
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.components.deleteCurrentModelLinkLabel
import org.jetbrains.completion.full.line.settings.ui.components.languageCheckBox
import org.jetbrains.completion.full.line.settings.ui.components.modelFromLocalFileLinkLabel
import org.jetbrains.completion.full.line.settings.ui.extended
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask

class LanguageLocalModelPanel(languages: Collection<Language>, private val flccEnabled: ComponentPredicate) : ComplexPanel {
  private val settings = MLServerCompletionSettings.getInstance()
  private val manager = service<ConfigurableModelsManager>()

  private val rows = languages.map {
    LanguageRow(
      it,
      MLServerCompletionSettings.getInstance().state.langStates.keys.maxByOrNull { id -> id.length },
    )
  }

  override val panel = panel {
    rows.forEach {
      it.row(this).enableIf(flccEnabled)
    }
    onGlobalReset {
      rows.forEach { it.innerActions.clear() }
    }
    onGlobalIsModified {
      rows.map { it.innerActions }.flatten().isNotEmpty()
    }
    onGlobalApply {
      if (settings.getModelMode() == ModelType.Local) SetupLocalModelsTask.queue(rows.map { it.innerActions }.flatten())
    }
  }.apply {
    name = ModelType.Local.name
  }

  private inner class LanguageRow(val language: Language, biggestLang: String?) : ComplexRow {
    private var current = manager.modelsSchema.models.find {
      it.currentLanguage == language.id.toLowerCase()
    }
      set(value) {
        onCurrentChange(value)
        field = value
      }
    val innerActions = mutableListOf<SetupLocalModelsTask.ToDoParams>()

    private val checkBox = languageCheckBox(language, biggestLang)
    private val modelStatus = ComponentPanelBuilder.createNonWrappingCommentComponent("")
    private val deleteCurrentModel = deleteCurrentModelLinkLabel(language, innerActions)
    private val modelFromLocalFile = modelFromLocalFileLinkLabel(language, innerActions)

    override fun row(builder: LayoutBuilder) = builder.row {
      component(checkBox).withSelectedBinding(settings.getLangState(language)::enabled.toBinding())
      extended {
        row {
          this.subRowIndent = 0
          row {
            cell {
              component(modelFromLocalFile)
              component(modelStatus)
            }
          }
          row {
            component(deleteCurrentModel)
          }
        }
      }
    }

    init {
      onCurrentChange(current)
      // Add download action for language if enabled, but not downloaded.
      if (service<ConfigurableModelsManager>().missedLanguage(language)) {
        innerActions.add(SetupLocalModelsTask.ToDoParams(language, SetupLocalModelsTask.Action.DOWNLOAD))
      }
    }

    private fun onCurrentChange(value: ModelSchema?) {
      if (value == null) {
        modelStatus.text = ""
      }
      else {
        modelStatus.text = message("fl.server.completion.models.source.local.comment", value.version, value.uid())
      }
    }
  }
}
