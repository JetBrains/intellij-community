package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.lang.Language
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.language.KeepKind
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.components.doubleTextField
import org.jetbrains.completion.full.line.settings.ui.components.intTextFieldFixed
import org.jetbrains.completion.full.line.settings.ui.enableSubRowsIf

class ExtendedSettingsPanel(
  language: Language,
  type: ModelType,
  flccEnabled: ComponentPredicate,
  langsEnabled: HashMap<String, ComponentPredicate>
) : ComplexPanel {
  private val settings = MLServerCompletionSettings.getInstance()

  val state = settings.getModelState(settings.getLangState(language), type)

  override val panel = panel {
    row {
      row(message("fl.server.completion.bs")) {
        if (language.id == "JAVA") {
          row {
            checkBox(message("fl.server.completion.enable.psi.completion"), state::psiBased)
          }
        }
        row(message("fl.server.completion.bs.num.iterations")) {
          intTextFieldFixed(state::numIterations, 1, IntRange(0, 50))
        }
        row(message("fl.server.completion.bs.beam.size")) {
          intTextFieldFixed(state::beamSize, 1, IntRange(0, 20))
        }
        row(message("fl.server.completion.bs.len.base")) {
          doubleTextField(state::lenBase, 1, IntRange(0, 10))
        }
        row(message("fl.server.completion.bs.len.pow")) {
          doubleTextField(state::lenPow, 1, IntRange(0, 1))
        }
        row(message("fl.server.completion.bs.diversity.strength")) {
          doubleTextField(state::diversityStrength, 1, IntRange(0, 10))
        }
        row(message("fl.server.completion.bs.diversity.groups")) {
          intTextFieldFixed(state::diversityGroups, 1, IntRange(0, 5))
          row {
            val groupUse = checkBox(
              message("fl.server.completion.group.top.n.use"),
              state::useGroupTopN
            ).selected
            row {
              intTextFieldFixed(state::groupTopN, 1, IntRange(0, 20))
                .enableIf(groupUse)
            }
          }
        }
        row {
          val groupUse = checkBox(
            message("fl.server.completion.context.length.use"),
            state::useCustomContextLength
          ).selected
          row {
            intTextFieldFixed(state::customContextLength, 1, IntRange(0, 384))
              .enableIf(groupUse)
          }
        }
        row(message("fl.server.completion.deduplication.minimum.prefix")) {
          doubleTextField(state::minimumPrefixDist, 1, IntRange(0, 1))
        }
        row(message("fl.server.completion.deduplication.minimum.edit")) {
          doubleTextField(state::minimumEditDist, 1, IntRange(0, 1))
        }
        row(message("fl.server.completion.deduplication.keep.kinds")) {
          cell {
            KeepKind.values().map { kind ->
              checkBox(
                kind.name.toLowerCase().capitalize(),
                { state.keepKinds.contains(kind) },
                { if (it) state.keepKinds.add(kind) else state.keepKinds.remove(kind) }
              )
            }
          }
        }
      }
    }.enableSubRowsIf(flccEnabled.and(langsEnabled.getValue(language.displayName)))
  }
}
