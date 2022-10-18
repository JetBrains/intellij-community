package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.lang.Language
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import org.jetbrains.completion.full.line.language.KeepKind
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.components.doubleTextField

class ExtendedSettingsPanel(
  language: Language,
  type: ModelType,
  flccEnabled: ComponentPredicate,
  langsEnabled: HashMap<String, ComponentPredicate>
) : ComplexPanel {
  private val settings = MLServerCompletionSettings.getInstance()

  val state = settings.getModelState(settings.getLangState(language), type)

  override val panel = panel {
    rowsRange {
      row {
        label(message("fl.server.completion.bs"))
      }
      indent {
        if (language.id == "JAVA") {
          row {
            checkBox(message("fl.server.completion.enable.psi.completion")).bindSelected(state::psiBased)
          }
        }
        row(message("fl.server.completion.bs.num.iterations")) {
          intTextField(IntRange(0, 50), 1)
            .columns(4)
            .bindIntText(state::numIterations)
        }
        row(message("fl.server.completion.bs.beam.size")) {
          intTextField(IntRange(0, 20), 1)
            .columns(4)
            .bindIntText(state::beamSize)
        }
        row(message("fl.server.completion.bs.len.base")) {
          doubleTextField(state::lenBase, IntRange(0, 10))
            .columns(4)
        }
        row(message("fl.server.completion.bs.len.pow")) {
          doubleTextField(state::lenPow, IntRange(0, 1))
            .columns(4)
        }
        row(message("fl.server.completion.bs.diversity.strength")) {
          doubleTextField(state::diversityStrength, IntRange(0, 10))
            .columns(4)
        }
        row(message("fl.server.completion.bs.diversity.groups")) {
          intTextField(IntRange(0, 5), 1)
            .columns(4)
            .bindIntText(state::diversityGroups)
        }
        indent {
          lateinit var groupUse: ComponentPredicate
          row {
            groupUse = checkBox(message("fl.server.completion.group.top.n.use"))
              .bindSelected(state::useGroupTopN)
              .selected
          }
          indent {
            row {
              intTextField(IntRange(0, 20), 1)
                .columns(4)
                .bindIntText(state::groupTopN)
                .enabledIf(groupUse)
            }
          }
        }
        lateinit var lengthUse: ComponentPredicate
        row {
          lengthUse = checkBox(message("fl.server.completion.context.length.use"))
            .bindSelected(state::useCustomContextLength)
            .selected
        }
        indent {
          row {
            intTextField(IntRange(0, 384), 1)
              .columns(4)
              .bindIntText(state::customContextLength)
              .enabledIf(lengthUse)
          }
        }
        panel {
          row(message("fl.server.completion.deduplication.minimum.prefix")) {
            doubleTextField(state::minimumPrefixDist, IntRange(0, 1))
              .columns(4)
          }
          row(message("fl.server.completion.deduplication.minimum.edit")) {
            doubleTextField(state::minimumEditDist, IntRange(0, 1))
              .columns(4)
          }
          row(message("fl.server.completion.deduplication.keep.kinds")) {
            KeepKind.values().map { kind ->
              @NlsSafe val text = kind.name.toLowerCase().capitalize()
              checkBox(text)
                .bindSelected({ state.keepKinds.contains(kind) },
                              { if (it) state.keepKinds.add(kind) else state.keepKinds.remove(kind) })
            }
          }
        }
      }
    }.enabledIf(flccEnabled.and(langsEnabled.getValue(language.displayName)))
  }
}
