package com.intellij.grazie.spellcheck.settings

import com.intellij.grazie.spellcheck.settings.SpellCheckerSettingsPane.WordsPanel
import com.intellij.ide.DataManager
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.spellchecker.DictionaryLayersProvider
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.SpellCheckerManager.Companion.restartInspections
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.settings.SpellCheckerSettings
import com.intellij.spellchecker.util.SpellCheckerBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class SpellCheckerConfigurable(private val project: Project) : BoundSearchableConfigurable(
  SpellCheckerBundle.message("spelling"),
  "reference.settings.ide.settings.spelling"
), NoScroll, WithEpDependencies {

  private val settings = SpellCheckerSettings.getInstance(project)
  private val manager = SpellCheckerManager.getInstance(project)
  private lateinit var myUseSingleDictionary: JBCheckBox
  private lateinit var myDictionariesComboBox: ComboBox<String>
  private lateinit var myDictionariesPanel: CustomDictionariesPanel
  private lateinit var wordsPanel: WordsPanel

  override fun createPanel(): DialogPanel {
    myDictionariesPanel = CustomDictionariesPanel(settings, project, manager)
    wordsPanel = WordsPanel(manager, disposable!!)

    return panel {
      row {
        myUseSingleDictionary = checkBox(SpellCheckerBundle.message("use.single.dictionary"))
          .gap(RightGap.SMALL)
          .bindSelected(settings::isUseSingleDictionaryToSave, settings::setUseSingleDictionaryToSave)
          .component
        myDictionariesComboBox = comboBox(DictionaryLayersProvider.getAllLayers(project).map { it.name })
          .enabledIf(myUseSingleDictionary.selected)
          .bindItem(settings::getDictionaryToSave, settings::setDictionaryToSave)
          .applyIfEnabled()
          .component
      }

      row {
        cell(myDictionariesPanel)
          .label(SpellCheckerBundle.message("add.dictionary.description", getSupportedDictionariesDescription()), LabelPosition.TOP)
          .align(AlignX.FILL)
          .onIsModified { myDictionariesPanel.isModified }
          .onReset { myDictionariesPanel.reset() }
      }

      row {
        cell(wordsPanel)
          .label(SpellCheckerBundle.message("settings.tab.accepted.words"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .onIsModified { wordsPanel.isModified }
          .onReset { wordsPanel.reset() }
      }

      row {
        link(SpellCheckerBundle.message("link.to.inspection.settings")) {
          val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(it.source as? JComponent))
          if (allSettings != null) {
            val errorsConfigurable = allSettings.find(ErrorsConfigurable::class.java)
            if (errorsConfigurable != null) {
              allSettings.select(errorsConfigurable).doWhenDone(
                Runnable { errorsConfigurable.selectInspectionTool(SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME) })
            }
          }
        }
      }
    }
  }

  override fun apply() {
    super.apply()

    if (wordsPanel.isModified()) {
      manager.updateUserDictionary(wordsPanel.getWords())
    }

    restartInspections(this)

    if (myDictionariesPanel.isModified()) {
      myDictionariesPanel.apply()
    }
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return setOf(CustomDictionaryProvider.EP_NAME)
  }

  private fun getSupportedDictionariesDescription(): String {
    return CustomDictionaryProvider.EP_NAME.extensionList
      .joinToString(", ", prefix = ", ") { it.dictionaryType }
  }
}
