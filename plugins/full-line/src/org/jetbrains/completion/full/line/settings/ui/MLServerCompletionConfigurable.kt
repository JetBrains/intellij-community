package org.jetbrains.completion.full.line.settings.ui

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBCardLayout
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.language.KeepKind
import org.jetbrains.completion.full.line.language.ModelState
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.ui.components.*
import org.jetbrains.completion.full.line.settings.ui.panels.CommonSettingsPanel
import org.jetbrains.completion.full.line.settings.ui.panels.ExtendedSettingsPanel
import org.jetbrains.completion.full.line.settings.ui.panels.LanguageCloudModelPanel
import org.jetbrains.completion.full.line.settings.ui.panels.LanguageLocalModelPanel
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel

class MLServerCompletionConfigurable : BoundConfigurable(message("fl.server.completion.display")), SearchableConfigurable {
  init {
    // It is necessary to preload the configuration services so that they are not reset
    service<MLServerCompletionSettings>()
    service<MlServerCompletionAuthState>()
  }

  private lateinit var flccEnabled: ComponentPredicate
  private val langsEnabled: HashMap<String, ComponentPredicate> = HashMap()

  private val settings = MLServerCompletionSettings.getInstance()
  private val settingsAuth = MlServerCompletionAuthState.getInstance()

  private val langExtendedPanel = DialogPanel(layout = JBCardLayout())
  private val langCommonPanel = DialogPanel(layout = JBCardLayout())
  private val modelPanel = DialogPanel(layout = DynamicCardLayout())
  private val languageBox = languageComboBox(langExtendedPanel)
  private val modelTypeBox = modelTypeComboBox(modelPanel)

  override fun createPanel(): DialogPanel {
    // Ui components: main panel (returned) consists of commonSettings
    // and if settings are expanded, langComboBox with langPanel are added
    return panel {
      titledRow(message("fl.server.completion.settings.group")) {
        row {
          flccEnabled = checkBox(message("fl.server.completion.display"), settings.state::enable).selected
        }

        // Connect dynamic configuration panels, based on visibility modifier
        setupLanguagesPanels(currentConfigurations())
        setupConfigPanels(currentConfigurations())

        // Common settings for all configurations
        commonSettings(modelPanel).enableSubRowsIf(flccEnabled)
        // separator line
        separatorRow()
        // Language-specific settings for each configuration
        languageSpecificSettings()
      }
    }.copyCallbacksFromChild(langExtendedPanel)
      .copyCallbacksFromChild(langCommonPanel)
      .copyCallbacksFromChild(modelPanel)
      .also {
        connectLanguageWithModelType(modelTypeBox, languageBox, langExtendedPanel)
        languageBox.addItemListener {
          if (it.stateChange == ItemEvent.SELECTED) {
            (langCommonPanel.layout as JBCardLayout).show(langCommonPanel, languageBox.selectedItem as String)
          }
        }

      }
  }

  private fun setupLanguagesPanels(configurations: List<Pair<Language, ModelType>>) {
    val languages = configurations.map { it.first }.toSet()
    // Step 1: clear configs
    modelPanel.removeAll()
    modelPanel.clearCallbacks()

    // Step 2: replace dropbox models
    languageBox.model = DefaultComboBoxModel(languages.map { it.id }.toTypedArray())

    // Step 3: add available configs
    val cloud = LanguageCloudModelPanel(languages, flccEnabled).panel
    val local = LanguageLocalModelPanel(languages, flccEnabled).panel

    modelPanel.copyCallbacksFromChild(cloud)
    modelPanel.add(cloud, cloud.name)
    modelPanel.copyCallbacksFromChild(local)
    modelPanel.add(local, local.name)

    // connect checkboxes for both model types
    cloud.languageCheckboxes().sortedBy { it.text }
      .zip(local.languageCheckboxes().sortedBy { it.text })
      .forEach { (cloudState, localState) ->
        require(cloudState.text == localState.text)

        langsEnabled[localState.text] = cloudState.selected
        localState.connectWith(cloudState)
        cloudState.connectWith(localState)
      }
  }

  private fun setupConfigPanels(configurations: List<Pair<Language, ModelType>>) {
    // Step 1: clear configs
    langCommonPanel.removeAll()
    langExtendedPanel.removeAll()

    langCommonPanel.clearCallbacks()
    langExtendedPanel.clearCallbacks()

    // Step 2: replace dropbox models
    modelTypeBox.model = DefaultComboBoxModel(configurations.map { it.second }.toSet().toTypedArray())

    // Step 3: add available configs
    configurations.map { it.first }.distinct().forEach { language ->
      val panel = CommonSettingsPanel(language, flccEnabled, langsEnabled).panel
      langCommonPanel.copyCallbacksFromChild(panel)
      langCommonPanel.add(panel, language.id)
    }
    configurations.forEach { (language, type) ->
      val panel = ExtendedSettingsPanel(language, type, flccEnabled, langsEnabled).panel
      langExtendedPanel.copyCallbacksFromChild(panel)
      langExtendedPanel.add(panel, languageConfigurationKey(language.id, type))
    }
  }

  private fun currentConfigurations(): List<Pair<Language, ModelType>> {
    return if (settingsAuth.isVerified()) {
      MLServerCompletionSettings.availableLanguages
        .map { language -> ModelType.values().map { language to it } }.flatten()
    }
    else {
      Language.findLanguageByID("Python")?.let { listOf(it to ModelType.Local) } ?: emptyList()
    }
  }

  private fun Row.commonSettings(modelPanel: DialogPanel): Row {
    return row {
      row(message("fl.server.completion.enable.languages")) {
        component(modelTypeBox).withModelTypeBinding(settings.state::modelType)
        row { component(modelPanel) }
      }
      //           TODO: uncomment when the new gray text method will be implemented
      //            row {
      //                checkBox(
      //                    message("fl.server.completion.gray.text"),
      //                    settings.state::useGrayText,
      //                )
      //            }
      row {
        val useBox = checkBox(message("fl.server.completion.top.n.use"), settings.state::useTopN)
        row {
          intTextFieldFixed(settings.state::topN, 1, IntRange(0, 20)).enableIf(useBox.selected)
        }
      }
    }
  }

  private fun Row.languageSpecificSettings(): Row {
    return row {
      row(message("fl.server.completion.settings.language")) {
        right {
          cell(isVerticalFlow = true) {
            component(languageBox)
            if (MLServerCompletionSettings.isExtended()) {
              link(message("full.line.settings.reset.default")) {
                settings.getModelState(Language.findLanguageByID(languageBox.item)!!, modelTypeBox.item)

                val languageState = settings.state.langStates.getValue(languageBox.item)

                when (modelTypeBox.item!!) {
                  ModelType.Cloud -> languageState.cloudModelState.fillFrom(ModelState())
                  ModelType.Local -> languageState.localModelState.fillFrom(ModelState())
                }
              }
            }
          }
        }
      }.enableIf(flccEnabled)
      row { component(langCommonPanel) }
      extended {
        row { component(langExtendedPanel) }
      }
    }
  }

  override fun getHelpTopic(): String {
    return "full.line.completion"
  }

  override fun getId(): String {
    return helpTopic
  }

  //TODO: move to ModelState
  private fun ModelState.fillFrom(inst: ModelState) {
    numIterations = inst.numIterations
    beamSize = inst.beamSize
    diversityGroups = inst.diversityGroups
    diversityStrength = inst.diversityStrength
    lenPow = inst.lenPow
    lenBase = inst.lenBase
    useGroupTopN = inst.useGroupTopN
    groupTopN = inst.groupTopN
    useCustomContextLength = inst.useCustomContextLength
    customContextLength = inst.customContextLength
    minimumPrefixDist = inst.minimumPrefixDist
    minimumEditDist = inst.minimumEditDist
    keepKinds = mutableSetOf<KeepKind>().apply { addAll(inst.keepKinds) }
    psiBased = inst.psiBased
  }
}
