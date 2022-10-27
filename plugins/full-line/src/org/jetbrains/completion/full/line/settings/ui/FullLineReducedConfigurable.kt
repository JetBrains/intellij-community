package org.jetbrains.completion.full.line.settings.ui

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.language.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.services.managers.excessLanguage
import org.jetbrains.completion.full.line.services.managers.missedLanguage
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.ui.components.*
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask
import kotlin.reflect.KMutableProperty1

@Suppress("DuplicatedCode")
class FullLineReducedConfigurable : BoundConfigurable(message("fl.server.completion.display")), SearchableConfigurable {
  init {
    // It is necessary to preload the configuration services so that they are not reset
    service<MLServerCompletionSettings>()
    service<MlServerCompletionAuthState>()
  }

  private val logger = thisLogger()

  private val settings = MLServerCompletionSettings.getInstance()
  private val settingsAuth = MlServerCompletionAuthState.getInstance()

  private lateinit var modelType: ComboBox<ModelType>
  private lateinit var flccEnabled: ComponentPredicate
  private val langsEnabled = mutableMapOf<String, ComponentPredicate>()

  private val languages = MLServerCompletionSettings.availableLanguages
  private val generalState = settings.state
  private val langStates = languages.sortedBy { it.id }.map { settings.getLangState(it) }
  private val modelStates = languages.sortedBy { it.id }.map { language ->
    ModelType.values().map { settings.getModelState(language, it) }
  }.flatten()

  private val biggestLang = languages.map { it.displayName }.maxByOrNull { it.length }

  override fun createPanel(): DialogPanel {
    return panel {
      group(message("fl.server.completion.settings.group")) {
        row {
          flccEnabled = checkBox(message("fl.server.completion.display"))
            .bindSelected(generalState::enable)
            .selected
          if (settingsAuth.isVerified()) {
            label(message("full.line.label.verified.text")).applyToComponent {
              foreground = JBColor(JBColor.GREEN.darker(), JBColor.GREEN.brighter())
            }
          }
        }

        indent {
          if (settingsAuth.isVerified()) {
            internal()
          }
          else {
            community()
          }
        }.enabledIf(flccEnabled)
      }
    }.also { panel ->
      // Unite callbacks for enabling same languages if more than on model typ provided
      if (settingsAuth.isVerified()) {
        with(LinkedHashMap<String, MutableList<JBCheckBox>>()) {
          panel.languageCheckboxes().groupByTo(this) { it.text }
          values.filter { it.size >= 2 }.forEach { (cloudState, localState) ->
            require(cloudState.text == localState.text)

            langsEnabled[localState.text] = cloudState.selected
            localState.connectWith(cloudState)
            cloudState.connectWith(localState)
          }
        }
      }
    }
  }

  private fun Panel.notAvailable(cause: String?) = row {
    logger.info("Settings are not available" + (cause?.let { ", cause: $cause." } ?: ""))
    comment("Currently, Full Line is available only for Python language, please install its plugin and restart or use PyCharm")
  }

  private fun Panel.community() {
    logger.info("Using community settings")
    val lang = Language.findLanguageByID("Python")
    if (lang == null) {
      notAvailable("Python plugin is missing")
      return
    }
    if (FullLineLanguageSupporter.getInstance(lang) == null) {
      notAvailable("Python supporter is missing")
      return
    }
    val langState = settings.getLangState(lang)

    buttonsGroup(message("fl.server.completion.enable.languages")) {
      localModels(listOf(lang))
    }
    buttonsGroup(message("fl.server.completion.settings.language")) {
      lateinit var useBox: Cell<JBCheckBox>
      row {
        useBox = checkBox(message("fl.server.completion.top.n.use")).bindSelected(generalState::useTopN)
      }
      indent {
        row { intTextField(IntRange(0, 20), 1)
          .bindIntText(generalState::topN)
          .enabledIf(useBox.selected) }
      }
      row(message("fl.server.completion.ref.check")) {
        comboBox(
          RedCodePolicy.values().toList(),
          RedCodePolicyRenderer()
        ).bindItem(langState::redCodePolicy.toNullableProperty())
      }
      row {
        checkBox(message("fl.server.completion.enable.strings.walking"))
          .bindSelected(langState::stringsWalking)
          .gap(RightGap.SMALL)
        contextHelp(message("fl.server.completion.enable.strings.walking.help"))
      }
      extended {
        row {
          checkBox(message("fl.server.completion.only.full"))
            .bindSelected(langState::onlyFullLines)
        }
        row {
          checkBox(message("fl.server.completion.group.answers"))
            .bindSelected(langState::groupAnswers)
        }
        row {
          checkBox(message("fl.server.completion.score"))
            .bindSelected(langState::showScore)
        }
      }
    }
  }

  private fun Panel.internal() {
    logger.info("Using internal settings")
    if (languages.isEmpty()) {
      notAvailable("No supported languages installed")
      return
    }

    row(message("full.line.settings.model.type")) {
      modelType = comboBox(ModelType.values().toList(), listCellRenderer { value, _, _ ->
        @NlsSafe val valueName = value.name
        text = valueName
        icon = value.icon
      }).bindItem(generalState::modelType.toNullableProperty())
        .component
    }.layout(RowLayout.INDEPENDENT)
    buttonsGroup(message("fl.server.completion.enable.languages")) {
      rowsRange {
        localModels(languages)
      }.visibleIf(modelType.selectedValueIs(ModelType.Local))
      rowsRange {
        cloudModels()
      }.visibleIf(modelType.selectedValueIs(ModelType.Cloud))
    }

    buttonsGroup(message("fl.server.completion.settings.completion")) {
      lateinit var useBox: Cell<JBCheckBox>
      row {
        useBox = checkBox(message("fl.server.completion.top.n.use"))
          .bindSelected(generalState::useTopN)
      }
      indent {
        row {
          intTextField(IntRange(0, 20), 1)
            .bindIntText(generalState::topN)
            .enabledIf(useBox.selected)
        }
      }
      row(message("fl.server.completion.ref.check")) {
        comboBox(RedCodePolicy.values().toList(),
          RedCodePolicyRenderer()
        ).bindItem(langStates.toMutableProperty(LangState::redCodePolicy).toNullableProperty())
      }.layout(RowLayout.INDEPENDENT)
      row {
        checkBox(message("fl.server.completion.enable.strings.walking"))
          .bindSelected(langStates.toMutableProperty(LangState::stringsWalking))
          .gap(RightGap.SMALL)
        contextHelp(message("fl.server.completion.enable.strings.walking.help"))
      }
      extended {
        row {
          checkBox(message("fl.server.completion.only.full"))
            .bindSelected(langStates.toMutableProperty(LangState::onlyFullLines))
        }
        row {
          checkBox(message("fl.server.completion.group.answers"))
            .bindSelected(langStates.toMutableProperty(LangState::groupAnswers))
        }
        row {
          checkBox(message("fl.server.completion.score"))
            .bindSelected(langStates.toMutableProperty(LangState::showScore))
        }
      }
    }
    extended {
      separator()
      row {
        label(message("fl.server.completion.bs"))
        link(message("full.line.settings.reset.default")) {
          generalState.langStates.keys.forEach {
            val lang = Language.findLanguageByID(it) ?: return@forEach
            generalState.langStates[it] = FullLineLanguageSupporter.getInstance(lang)?.langState ?: LangState()
          }
        }.align(AlignX.RIGHT)
      }
      row(message("fl.server.completion.bs.num.iterations")) {
        intTextField(IntRange(0, 50), 1)
          .bindIntText(modelStates.toMutableProperty(ModelState::numIterations))
      }
      row(message("fl.server.completion.bs.beam.size")) {
        intTextField(IntRange(0, 20), 1)
          .bindIntText(modelStates.toMutableProperty(ModelState::beamSize))
      }
      row(message("fl.server.completion.bs.len.base")) {
        doubleTextField(modelStates.toMutableProperty(ModelState::lenBase), IntRange(0, 10))
      }
      row(message("fl.server.completion.bs.len.pow")) {
        doubleTextField(modelStates.toMutableProperty(ModelState::lenPow), IntRange(0, 1))
      }
      row(message("fl.server.completion.bs.diversity.strength")) {
        doubleTextField(modelStates.toMutableProperty(ModelState::diversityStrength), IntRange(0, 10))
      }
      row(message("fl.server.completion.bs.diversity.groups")) {
        intTextField(IntRange(0, 5), 1)
          .bindIntText(modelStates.toMutableProperty(ModelState::diversityGroups))
      }
      indent {
        lateinit var groupUse: ComponentPredicate
        row {
          groupUse = checkBox(message("fl.server.completion.group.top.n.use"))
            .bindSelected(modelStates.toMutableProperty(ModelState::useGroupTopN))
            .selected
        }
        indent {
          row {
            intTextField(IntRange(0, 20), 1)
              .bindIntText(modelStates.toMutableProperty(ModelState::groupTopN))
              .enabledIf(groupUse)
          }
        }
      }

      lateinit var lengthUse: ComponentPredicate
      row {
        lengthUse = checkBox(message("fl.server.completion.context.length.use"))
          .bindSelected(modelStates.toMutableProperty(ModelState::useCustomContextLength))
          .selected
      }
      indent {
        row {
          intTextField(IntRange(0, 384), 1)
            .bindIntText(modelStates.toMutableProperty(ModelState::customContextLength))
            .enabledIf(lengthUse)
        }
      }
      panel {
        row(message("fl.server.completion.deduplication.minimum.prefix")) {
          doubleTextField(modelStates.toMutableProperty(ModelState::minimumPrefixDist), IntRange(0, 1))
        }
        row(message("fl.server.completion.deduplication.minimum.edit")) {
          doubleTextField(modelStates.toMutableProperty(ModelState::minimumEditDist), IntRange(0, 1))
        }
        row(message("fl.server.completion.deduplication.keep.kinds")) {
          KeepKind.values().map { kind ->
            @NlsSafe val text = kind.name.toLowerCase().capitalize()
            checkBox(text)
              .bindSelected({ modelStates.first().keepKinds.contains(kind) },
                            { action ->
                              modelStates.forEach {
                                if (action) it.keepKinds.add(kind) else it.keepKinds.remove(kind)
                              }
                            })
          }
        }
      }
    }
    //        if (language == JavaLanguage.INSTANCE) {
    //            row {
    //                checkBox(message("fl.server.completion.enable.psi.completion"), state::psiBased)
    //            }
    //        }
  }

  private fun Panel.cloudModels() {
    languages.forEach { language ->
      row {
        val checkBox = languageCheckBox(language, biggestLang)
          .bindSelected(MLServerCompletionSettings.getInstance().getLangState(language)::enabled)
        val loadingIcon = LoadingComponent()

        cell(pingButton(language, loadingIcon, null))
          .enabledIf(checkBox.selected)
        loadingStatus(loadingIcon).forEach {
          // Do not show even, if model type was changed. Visibility will be controlled from LoadingComponent itself
          it.enabledIf(checkBox.selected).visibleIf(modelType.selectedValueMatches { false })
        }
      }
    }
  }

  private fun Panel.localModels(languages: List<Language>) {
    val actions = mutableListOf<SetupLocalModelsTask.ToDoParams>()

    languages.map { language ->
      row {
        languageCheckBox(language, biggestLang).bindSelected(settings.getLangState(language)::enabled)
      }
      extended {
        indent {
          row {
            cell(modelFromLocalFileLinkLabel(language, actions))
            service<ConfigurableModelsManager>().modelsSchema.models
              .find { it.currentLanguage == language.id.toLowerCase() }
              ?.let {
                comment(message("fl.server.completion.models.source.local.comment", it.version, it.uid()))
              }
          }
          //                    Removed cause deleting now exec after language turn off
          //                    row {
          //                        component(deleteCurrentModelLinkLabel(language, actions))
          //                    }
        }
      }
    }

    var resetCount = 0
    onReset {
      // Add download actions for languages which are enabled, but not downloaded.
      // resetCount flag used cause reset is called firstly called right after components initialisation
      resetCount++
    }
    onIsModified {
      settings.getModelMode() == ModelType.Local
      && ((resetCount <= 1 && languages.any { service<ConfigurableModelsManager>().missedLanguage(it) }) || actions.isNotEmpty())
    }
    onApply {
      if (settings.getModelMode() == ModelType.Local) {
        val taskActions = mutableListOf<SetupLocalModelsTask.ToDoParams>()

        // Add actions to import local
        taskActions.addAll(actions)
        actions.clear()

        // Add actions to remove local models which are downloaded, but disabled
        taskActions.addAll(
          languages.filter {
            service<ConfigurableModelsManager>().excessLanguage(it)
          }.map {
            SetupLocalModelsTask.ToDoParams(it, SetupLocalModelsTask.Action.REMOVE)
          }
        )
        // Add actions to download local models which are removed, but enabled
        taskActions.addAll(
          languages.filter {
            service<ConfigurableModelsManager>().missedLanguage(it)
          }.map {
            SetupLocalModelsTask.ToDoParams(it, SetupLocalModelsTask.Action.DOWNLOAD)
          }
        )
        SetupLocalModelsTask.queue(taskActions)
      }
    }
  }

  override fun getHelpTopic() = "full.line.completion.reduced"
  override fun getId() = helpTopic
  override fun getDisplayName() = message("full.line.configurable.name")

}

/**
 * Below mutable property to create settings for all language/modeltype states.
 * Taking value from first language (sorted by id)
 * Setting value for all states at ones
 */
private fun <T, V> List<T>.toMutableProperty(field: KMutableProperty1<T, V>): MutableProperty<V> {
  val bindings = map { MutableProperty({ field.getter(it) }, { value ->  field.setter(it, value) }) }
  return MutableProperty({ bindings.first().get() }, { v -> bindings.forEach { it.set(v) } })
}
