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
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.spellchecker.DictionaryLayersProvider
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.SpellCheckerManager.Companion.bundledDictionaries
import com.intellij.spellchecker.SpellCheckerManager.Companion.restartInspections
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.settings.SpellCheckerSettings
import com.intellij.spellchecker.util.SpellCheckerBundle
import com.intellij.ui.OptionalChooserComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox

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

  // Dictionaries provided by plugins -- runtime and bundled
  // todo myProvidedDictionariesChooserComponent is not used since WI-53660 Simplify spellchecker settings: remove bundled dictionaries panel
  private val myProvidedDictionariesChooserComponent: OptionalChooserComponent<String>
  private val runtimeDictionaries = mutableSetOf<String>()
  private val providedDictionaries = mutableListOf<Pair<String, Boolean>>()

  init {
    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillProvidedDictionaries()

    myProvidedDictionariesChooserComponent = object : OptionalChooserComponent<String>(providedDictionaries) {

      public override fun createCheckBox(path: String, checked: Boolean): JCheckBox {
        return JCheckBox(FileUtil.toSystemDependentName(path), checked)
      }

      override fun apply() {
        super.apply()

        val runtimeDisabledDictionaries = mutableSetOf<String>()

        for (pair in providedDictionaries) {
          if (pair.second) continue

          if (runtimeDictionaries.contains(pair.first)) {
            runtimeDisabledDictionaries.add(pair.first)
          }
        }
        settings.runtimeDisabledDictionariesNames = runtimeDisabledDictionaries
      }

      override fun reset() {
        super.reset()
        fillProvidedDictionaries()
      }
    }

    myProvidedDictionariesChooserComponent.emptyText.text = SpellCheckerBundle.message("no.dictionaries")
  }

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
          val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext())
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
    if (myProvidedDictionariesChooserComponent.isModified) {
      myProvidedDictionariesChooserComponent.apply()
    }

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

  private fun fillProvidedDictionaries() {
    providedDictionaries.clear()

    for (dictionary in bundledDictionaries) {
      providedDictionaries.add(Pair.create(dictionary, true))
    }

    runtimeDictionaries.clear()
    for (dictionary in SpellCheckerManager.runtimeDictionaries.map { it.name }) {
      runtimeDictionaries.add(dictionary)
      providedDictionaries.add(Pair.create(dictionary, !settings.runtimeDisabledDictionariesNames.contains(dictionary)))
    }
  }
}
