// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.Link
import com.intellij.ui.components.labels.ActionLink
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

private val settings get() = UISettings.instance
private val fontOptions get() = AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl
private val defaultProject get() = ProjectManager.getInstance().defaultProject

private val laf get() = LafManager.getInstance()
private val keymapManager get() = KeymapManager.getInstance() as KeymapManagerImpl
private val editorColorsManager get() = EditorColorsManager.getInstance() as EditorColorsManagerImpl

class CustomizeTabFactory : WelcomeTabFactory {
  override fun createWelcomeTab(parentDisposable: Disposable) = CustomizeTab(parentDisposable)
}

private fun getIdeFont() = if (settings.overrideLafFonts) settings.fontSize else JBFont.label().size
private fun getEditorFont() = fontOptions.getSize(fontOptions.fontFamily)

class CustomizeTab(parentDisposable: Disposable) : DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.customize.title")) {
  private val supportedColorBlindness = getColorBlindness()
  private val propertyGraph = PropertyGraph()
  private val lafProperty = propertyGraph.graphProperty { laf.lookAndFeelReference }
  private val syncThemeProperty = propertyGraph.graphProperty { laf.autodetect }
  private val ideFontProperty = propertyGraph.graphProperty { getIdeFont() }
  private val editorFontProperty = propertyGraph.graphProperty { getEditorFont() }
  private val keymapProperty = propertyGraph.graphProperty { keymapManager.activeKeymap }
  private val colorBlindnessProperty = propertyGraph.graphProperty { settings.colorBlindness ?: supportedColorBlindness.firstOrNull() }
  private val adjustColorsProperty = propertyGraph.graphProperty { settings.colorBlindness != null }

  init {
    lafProperty.afterChange({ QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, laf.findLaf(it), true) }, parentDisposable)
    syncThemeProperty.afterChange { laf.autodetect = it }
    ideFontProperty.afterChange({
                                  settings.overrideLafFonts = true
                                  settings.fontSize = it
                                  updateFontSettingsLater()
                                }, parentDisposable)
    editorFontProperty.afterChange({
                                     fontOptions.setSize(fontOptions.fontFamily, it)
                                     updateFontSettingsLater()
                                   }, parentDisposable)
    keymapProperty.afterChange({ keymapManager.activeKeymap = it }, parentDisposable)
    adjustColorsProperty.afterChange({ updateColorBlindness() }, parentDisposable)
    colorBlindnessProperty.afterChange({ updateColorBlindness() }, parentDisposable)

    val busConnection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateProperty(ideFontProperty) { getIdeFont() } })
    busConnection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { updateAccessibilityProperties() })
    busConnection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        updateProperty(keymapProperty) { keymapManager.activeKeymap }
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
    updateProperty(editorFontProperty) { getEditorFont() }
    val adjustColorSetting = settings.colorBlindness != null
    updateProperty(adjustColorsProperty) { adjustColorSetting }
    if (adjustColorSetting) {
      updateProperty(colorBlindnessProperty) { settings.colorBlindness }
    }
  }

  override fun buildComponent(): JComponent {
    return panel {
      blockRow {
        header(IdeBundle.message("welcome.screen.color.theme.header"))
        fullRow {
          val theme = comboBox(laf.lafComboBoxModel, lafProperty, laf.lookAndFeelCellRenderer)
          val syncCheckBox = checkBox(IdeBundle.message("preferred.theme.autodetect.selector"), syncThemeProperty).
                              withLargeLeftGap().
                              apply {
                                component.isOpaque = false
                                component.isVisible = laf.autodetectSupported
                              }

          theme.enableIf(syncCheckBox.selected.not())
          component(laf.settingsToolbar).visibleIf(syncCheckBox.selected).withLeftGap()
        }
      }.largeGapAfter()
      blockRow {
        header(IdeBundle.message("title.accessibility"))

        row(IdeBundle.message("welcome.screen.ide.font.size.label"))
        {
          fontComboBox(ideFontProperty)
        }
        row(IdeBundle.message("welcome.screen.editor.font.size.label"))
        {
          fontComboBox(editorFontProperty)
        }.largeGapAfter()

        createColorBlindnessSettingBlock()
      }.largeGapAfter()
      blockRow {
        header(KeyMapBundle.message("keymap.display.name"))
        fullRow {
          comboBox(DefaultComboBoxModel(getKeymaps().toTypedArray()), keymapProperty)
          component(focusableLink(KeyMapBundle.message("welcome.screen.keymap.configure.link")) {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, KeyMapBundle.message("keymap.display.name"))
          }).withLargeLeftGap()
        }
      }
      blockRow {
        val action = ActionManager.getInstance().getAction("WelcomeScreen.Configure.Import")
        component(ActionLink(action.templateText, null, action).apply { isFocusable = true })
        row {
          component(focusableLink(IdeBundle.message("welcome.screen.all.settings.link")) {
            ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject,
                                                              *ShowSettingsUtilImpl.getConfigurableGroups(defaultProject, true))
          })
        }
      }
    }.withBorder(JBUI.Borders.empty(23, 30, 20, 20))
      .withBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground())
  }

  private fun Row.createColorBlindnessSettingBlock() {
    if (supportedColorBlindness.isNotEmpty()) {
      fullRow {
        if (supportedColorBlindness.size == 1) {
          checkBox(UIBundle.message("color.blindness.checkbox.text"), adjustColorsProperty,
                   UIBundle.message("color.blindness.checkbox.comment")).applyToComponent { isOpaque = false }
        }
        else {
          val checkBox = checkBox(UIBundle.message("welcome.screen.color.blindness.combobox.text"),
                                  adjustColorsProperty).applyToComponent { isOpaque = false }.component
          comboBox(DefaultComboBoxModel(supportedColorBlindness.toTypedArray()), colorBlindnessProperty, SimpleListCellRenderer.create("") {
            PlatformEditorBundle.message(it?.key ?: "")
          }).comment(UIBundle.message("color.blindness.combobox.comment")).enableIf(checkBox.selected)
        }
        component(focusableLink(UIBundle.message("color.blindness.link.to.help"))
                  { HelpManager.getInstance().invokeHelp("Colorblind_Settings") })
          .withLargeLeftGap()
      }
    }
  }

  private fun Row.header(@Nls title: String) {
    fullRow {
      component(HeaderLabel(title))
    }.largeGapAfter()
  }

  private class HeaderLabel(@Nls title: String) : JBLabel(title) {
    override fun setUI(ui: LabelUI?) {
      super.setUI(ui)
      if (font != null) {
        font = FontUIResource(font.deriveFont(font.size2D + JBUIScale.scale(3)).deriveFont(Font.BOLD))
      }
    }
  }

  private fun focusableLink(@NlsContexts.Label text: String, action: () -> Unit): JComponent {
    return Link(text, null, action).apply { isFocusable = true }
  }

  private fun Cell.fontComboBox(fontProperty: GraphProperty<Int>): CellBuilder<ComboBox<Int>> {
    val fontSizes = UIUtil.getStandardFontSizes().map { Integer.valueOf(it) }.toSortedSet()
    fontSizes.add(fontProperty.get())
    val model = DefaultComboBoxModel(fontSizes.toTypedArray())
    return comboBox(model, fontProperty).applyToComponent {
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