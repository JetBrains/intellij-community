// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ShowSettingsUtil.getSettingsMenuName
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Link
import com.intellij.ui.layout.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

private val settings get() = UISettings.instance
private val fontOptions get() = AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl
private val laf get() = LafManager.getInstance()
private val keymapManager get() = KeymapManager.getInstance() as KeymapManagerImpl

class CustomizeTabFactory : WelcomeTabFactory {
  override fun createWelcomeTab(parentDisposable: Disposable) = CustomizeTab()
}

class CustomizeTab : DefaultWelcomeScreenTab("Customize") {
  override fun buildComponent(): JComponent {

    return panel {
      blockRow {
        header(IdeBundle.message("welcome.screen.color.theme.header"))
        row {
          comboBox(laf.lafComboBoxModel,
                   { laf.currentLookAndFeelReference },
                   { QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, laf.findLaf(it), true) }).applyIfEnabled()
        }
      }.largeGapAfter()
      blockRow {
        header(IdeBundle.message("title.accessibility"))

        row(IdeBundle.message("welcome.screen.ide.font.size.label"))
        {
          fontSizeComboBox({ settings.fontSize },
                           { settings.fontSize = it },
                           settings.fontSize)
          //.shouldUpdateLaF()
        }
        row(IdeBundle.message("welcome.screen.editor.font.size.label"))
        {
          fontSizeComboBox({ fontOptions.getSize(fontOptions.fontFamily) },
                           { fontOptions.setSize(fontOptions.fontFamily, it) },
                           fontOptions.getSize(fontOptions.fontFamily))
          //.shouldUpdateLaF()
        }.largeGapAfter()

        createColorBlindnessSettingBlock()

      }.largeGapAfter()
      blockRow {
        header(KeyMapBundle.message("keymap.display.name"))
        fullRow {
          comboBox(DefaultComboBoxModel(getKeymaps().toTypedArray()), { keymapManager.activeKeymap }, { keymapManager.activeKeymap = it!! })
          component(Link(KeyMapBundle.message("welcome.screen.keymap.configure.link"))
                    { ShowSettingsUtil.getInstance().showSettingsDialog(null, KeyMapBundle.message("keymap.display.name")) })
            .withLargeLeftGap()
        }
      }
      blockRow {
        component(Link(IdeBundle.message("welcome.screen.all.settings.link"))
                  { ShowSettingsUtil.getInstance().showSettingsDialog(null, getSettingsMenuName()) })
      }
    }.withBorder(JBUI.Borders.empty(23, 30, 20, 20))
      .withBackground(WelcomeScreenUIManager.getCustomizeBackground())
  }

  private fun Row.createColorBlindnessSettingBlock() {
    val supportedValues = ColorBlindness.values().filter { ColorBlindnessSupport.get(it) != null }
    if (supportedValues.isNotEmpty()) {
      val modelBinding = PropertyBinding({ settings.colorBlindness }, { settings.colorBlindness = it })
      val onApply = {
        // callback executed not when all changes are applied, but one component by one, so, reload later when everything were applied
        ApplicationManager.getApplication().invokeLater(Runnable {
          DefaultColorSchemesManager.getInstance().reload()
          (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
        })
      }

      fullRow {
        if (supportedValues.size == 1) {
          val jbCheckBox = JBCheckBox(UIBundle.message("color.blindness.checkbox.text"))
          jbCheckBox.isOpaque = false
          component(jbCheckBox)
            .withBinding({ if (it.isSelected) supportedValues.first() else null },
                         { it, value -> it.isSelected = value != null },
                         modelBinding)
            .comment(UIBundle.message("color.blindness.checkbox.comment"))
        }
        else {
          val enableColorBlindness = component(
            JBCheckBox(UIBundle.message("welcome.screen.color.blindness.combobox.text"))).applyToComponent {
            isSelected = modelBinding.get() != null
            isOpaque = false
          }
          component<ComboBox<ColorBlindness>>(ComboBox(supportedValues.toTypedArray()))
            .enableIf(enableColorBlindness.selected)
            .applyToComponent { renderer = SimpleListCellRenderer.create("") { PlatformEditorBundle.message(it.key) } }
            .comment(UIBundle.message("color.blindness.combobox.comment"))
            .withBinding({ if (enableColorBlindness.component.isSelected) it.selectedItem as? ColorBlindness else null },
                         { it, value -> it.selectedItem = value ?: supportedValues.first() },
                         modelBinding)
            .onApply(onApply)
        }
        component(Link(UIBundle.message("color.blindness.link.to.help"))
                  { HelpManager.getInstance().invokeHelp("Colorblind_Settings") })
          .withLargeLeftGap()
      }
    }
  }

  private fun Row.header(@Nls title: String) {
    fullRow {
      label(title).apply { component.font = component.font.deriveFont(JBUIScale.scale(16)).deriveFont(Font.BOLD) }
    }.largeGapAfter()
  }

  private fun getKeymaps(): List<Keymap> {
    return keymapManager.getKeymaps(KeymapSchemeManager.FILTER).sortedWith(keymapComparator)
  }
}