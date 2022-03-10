// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

private val settings get() = UISettings.getInstance()
private val defaultProject get() = ProjectManager.getInstance().defaultProject

private val laf get() = LafManager.getInstance()
private val keymapManager get() = KeymapManager.getInstance() as KeymapManagerImpl
private val editorColorsManager get() = EditorColorsManager.getInstance() as EditorColorsManagerImpl

class CustomizeTabFactory : WelcomeTabFactory {
  override fun createWelcomeTab(parentDisposable: Disposable) = CustomizeTab(parentDisposable)
}

private fun getIdeFont() = if (settings.overrideLafFonts) settings.fontSize2D else JBFont.label().size2D

class CustomizeTab(parentDisposable: Disposable) : DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.customize.title"),
                                                                           WelcomeScreenEventCollector.TabType.TabNavCustomize) {
  private val supportedColorBlindness = getColorBlindness()
  private val propertyGraph = PropertyGraph()
  private val lafProperty = propertyGraph.graphProperty { laf.lookAndFeelReference }
  private val syncThemeProperty = propertyGraph.graphProperty { laf.autodetect }
  private val ideFontProperty = propertyGraph.graphProperty { getIdeFont() }
  private val keymapProperty = propertyGraph.graphProperty { keymapManager.activeKeymap }
  private val colorBlindnessProperty = propertyGraph.graphProperty { settings.colorBlindness ?: supportedColorBlindness.firstOrNull() }
  private val adjustColorsProperty = propertyGraph.graphProperty { settings.colorBlindness != null }

  private var keymapComboBox: ComboBox<Keymap>? = null
  private var colorThemeComboBox: ComboBox<LafManager.LafReference>? = null

  init {
    lafProperty.afterChange({
                              val newLaf = laf.findLaf(it)
                              if (laf.currentLookAndFeel == newLaf) return@afterChange
                              QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, newLaf, true)
                              WelcomeScreenEventCollector.logLafChanged(newLaf, laf.autodetect)
                            }, parentDisposable)
    syncThemeProperty.afterChange {
      if (laf.autodetect == it) return@afterChange
      laf.autodetect = it
      WelcomeScreenEventCollector.logLafChanged(laf.currentLookAndFeel, laf.autodetect)
    }
    ideFontProperty.afterChange({
                                  if (settings.fontSize2D == it) return@afterChange
                                  settings.overrideLafFonts = true
                                  WelcomeScreenEventCollector.logIdeFontChanged(settings.fontSize2D, it)
                                  settings.fontSize2D = it
                                  updateFontSettingsLater()
                                }, parentDisposable)
    keymapProperty.afterChange({
                                 if (keymapManager.activeKeymap == it) return@afterChange
                                 WelcomeScreenEventCollector.logKeymapChanged(it)
                                 keymapManager.activeKeymap = it
                               }, parentDisposable)
    adjustColorsProperty.afterChange({
                                       if (adjustColorsProperty.get() == (settings.colorBlindness != null)) return@afterChange
                                       WelcomeScreenEventCollector.logColorBlindnessChanged(adjustColorsProperty.get())
                                       updateColorBlindness()
                                     }, parentDisposable)
    colorBlindnessProperty.afterChange({ updateColorBlindness() }, parentDisposable)

    val busConnection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateProperty(ideFontProperty) { getIdeFont() } })
    busConnection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { updateAccessibilityProperties() })
    busConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      updateProperty(lafProperty) { laf.lookAndFeelReference }
      updateLafs()
    })
    busConnection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        updateProperty(keymapProperty) { keymapManager.activeKeymap }
        updateKeymaps()
      }

      override fun keymapAdded(keymap: Keymap) {
        updateKeymaps()
      }

      override fun keymapRemoved(keymap: Keymap) {
        updateKeymaps()
      }
    })
  }

  private fun updateColorBlindness() {
    settings.colorBlindness = if (adjustColorsProperty.get()) colorBlindnessProperty.get() else null
    ApplicationManager.getApplication().invokeLater(Runnable {
      DefaultColorSchemesManager.getInstance().reload()
      editorColorsManager.schemeChangedOrSwitched(null)
    })
  }

  private fun updateFontSettingsLater() {
    ApplicationManager.getApplication().invokeLater {
      laf.updateUI()
      settings.fireUISettingsChanged()
    }
  }

  private fun <T> updateProperty(property: GraphProperty<T>, settingGetter: () -> T) {
    val value = settingGetter()
    if (property.get() != value) {
      property.set(value)
    }
  }

  private fun updateAccessibilityProperties() {
    val adjustColorSetting = settings.colorBlindness != null
    updateProperty(adjustColorsProperty) { adjustColorSetting }
    if (adjustColorSetting) {
      updateProperty(colorBlindnessProperty) { settings.colorBlindness }
    }
  }

  override fun buildComponent(): JComponent {
    return panel {
      header(IdeBundle.message("welcome.screen.color.theme.header"), true)
      row {
        val themeBuilder = comboBox(laf.lafComboBoxModel, laf.lookAndFeelCellRenderer)
          .bindItem(lafProperty)
          .accessibleName(IdeBundle.message("welcome.screen.color.theme.header"))
        colorThemeComboBox = themeBuilder.component
        val syncCheckBox = checkBox(IdeBundle.message("preferred.theme.autodetect.selector"))
          .bindSelected(syncThemeProperty)
          .applyToComponent {
            isOpaque = false
            isVisible = laf.autodetectSupported
          }

        themeBuilder.enabledIf(syncCheckBox.selected.not())
        cell(laf.settingsToolbar).visibleIf(syncCheckBox.selected)
      }

      header(IdeBundle.message("title.accessibility"))

      row(IdeBundle.message("welcome.screen.ide.font.size.label")) {
        fontComboBox(ideFontProperty)
      }.bottomGap(BottomGap.SMALL)

      createColorBlindnessSettingBlock()

      header(KeyMapBundle.message("keymap.display.name"))
      row {
        keymapComboBox = comboBox(DefaultComboBoxModel(getKeymaps().toTypedArray()))
          .bindItem(keymapProperty)
          .accessibleName(KeyMapBundle.message("keymap.display.name"))
          .component
        link(KeyMapBundle.message("welcome.screen.keymap.configure.link")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject, KeyMapBundle.message("keymap.display.name"))
        }
      }

      row {
        cell(AnActionLink("WelcomeScreen.Configure.Import", ActionPlaces.WELCOME_SCREEN))
      }.topGap(TopGap.MEDIUM)
      row {
        link(IdeBundle.message("welcome.screen.all.settings.link")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject,
                                                            *ShowSettingsUtilImpl.getConfigurableGroups(defaultProject, true))
        }
      }
    }.withBorder(JBUI.Borders.empty(23, 30, 20, 20))
      .withBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground())
  }

  private fun updateKeymaps() {
    (keymapComboBox?.model as DefaultComboBoxModel?)?.apply {
      removeAllElements()
      addAll(getKeymaps())
      selectedItem = keymapProperty.get()
    }
  }

  private fun updateLafs() {
    colorThemeComboBox?.apply {
      model = laf.lafComboBoxModel
      selectedItem = lafProperty.get()
    }
  }

  private fun Panel.createColorBlindnessSettingBlock() {
    if (supportedColorBlindness.isNotEmpty()) {
      row {
        if (supportedColorBlindness.size == 1) {
          checkBox(UIBundle.message("color.blindness.checkbox.text"))
            .bindSelected(adjustColorsProperty)
            .comment(UIBundle.message("color.blindness.checkbox.comment"))
            .applyToComponent { isOpaque = false }
        }
        else {
          val checkBox = checkBox(UIBundle.message("welcome.screen.color.blindness.combobox.text"))
            .bindSelected(adjustColorsProperty)
            .applyToComponent { isOpaque = false }.component
          comboBox(DefaultComboBoxModel(supportedColorBlindness.toTypedArray()), SimpleListCellRenderer.create("") {
            PlatformEditorBundle.message(it?.key ?: "")
          })
            .bindItem(colorBlindnessProperty)
            .comment(UIBundle.message("color.blindness.combobox.comment"))
            .enabledIf(checkBox.selected)
        }
        link(UIBundle.message("color.blindness.link.to.help"))
        { HelpManager.getInstance().invokeHelp("Colorblind_Settings") }
      }
    }
  }

  private fun Panel.header(@Nls title: String, firstHeader: Boolean = false) {
    val row = row {
      cell(HeaderLabel(title))
    }.bottomGap(BottomGap.SMALL)
    if (!firstHeader) {
      row.topGap(TopGap.MEDIUM)
    }
  }

  private class HeaderLabel(@Nls title: String) : JBLabel(title) {
    override fun setUI(ui: LabelUI?) {
      super.setUI(ui)
      if (font != null) {
        font = FontUIResource(font.deriveFont(font.size2D + JBUIScale.scale(3)).deriveFont(Font.BOLD))
      }
    }
  }

  private fun Row.fontComboBox(fontProperty: GraphProperty<Float>): Cell<ComboBox<Float>> {
    val fontSizes = UIUtil.getStandardFontSizes().map { it.toFloat() }.toSortedSet()
    fontSizes.add(fontProperty.get())
    val model = DefaultComboBoxModel(fontSizes.toTypedArray())
    return comboBox(model)
      .bindItem(fontProperty)
      .applyToComponent {
        isEditable = true
      }
  }

  private fun getColorBlindness(): List<ColorBlindness> {
    return ColorBlindness.values().asList().filter { ColorBlindnessSupport.get(it) != null }
  }

  private fun getKeymaps(): List<Keymap> {
    return keymapManager.getKeymaps(KeymapSchemeManager.FILTER).sortedWith(keymapComparator)
  }
}