// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.SchemesPanel
import com.intellij.application.options.colors.SchemesPanelFactory
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.*
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.ide.ui.localization.statistics.EventSource
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
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
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

private val settings: UISettings
  get() = UISettings.getInstance()
private val defaultProject: Project
  get() = ProjectManager.getInstance().defaultProject

private val laf: LafManager
  get() = LafManager.getInstance()
private val keymapManager: KeymapManagerImpl
  get() = KeymapManager.getInstance() as KeymapManagerImpl

class CustomizeTabFactory : WelcomeTabFactory {
  override fun createWelcomeTabs(ws: WelcomeScreen, parentDisposable: Disposable): MutableList<WelcomeScreenTab> {
    return mutableListOf(CustomizeTab(parentDisposable))
  }
}

private fun getIdeFontSize(): Float {
  return if (settings.overrideLafFonts) settings.fontSize2D else getDefaultIdeFont().size2D
}

private fun getIdeFontName(): @NlsSafe String? {
  return if (settings.overrideLafFonts) settings.fontFace else getDefaultIdeFont().family
}

private fun getDefaultIdeFont() = (LafManager.getInstance() as? LafManagerImpl)?.defaultFont ?: JBFont.label()

private class CustomizeTab(val parentDisposable: Disposable) : DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.customize.title"),
                                                                           WelcomeScreenEventCollector.TabType.TabNavCustomize) {
  private val supportedColorBlindness = getColorBlindness()
  private val propertyGraph = PropertyGraph()
  private val lafProperty = propertyGraph.lazyProperty { laf.lookAndFeelReference }
  private val syncThemeProperty = propertyGraph.lazyProperty { laf.autodetect }
  private val ideFontProperty = propertyGraph.lazyProperty { getIdeFontSize() }
  private val keymapProperty = propertyGraph.lazyProperty { keymapManager.activeKeymap }
  private val colorBlindnessProperty = propertyGraph.lazyProperty { settings.colorBlindness ?: supportedColorBlindness.firstOrNull() }
  private val adjustColorsProperty = propertyGraph.lazyProperty { settings.colorBlindness != null }
  private val lafConnection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable)


  private var keymapComboBox: ComboBox<Keymap>? = null
  private var colorThemeComboBox: ComboBox<LafReference>? = null

  init {
    lafProperty.afterChange(parentDisposable) {
      val newLaf = laf.findLaf(it.themeId)
      if (laf.getCurrentUIThemeLookAndFeel() == newLaf) {
        return@afterChange
      }

      ApplicationManager.getApplication().invokeLater {
        QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, newLaf, true)
        WelcomeScreenEventCollector.logLafChanged(newLaf, laf.autodetect)
      }
    }
    syncThemeProperty.afterChange {
      if (laf.autodetect == it) return@afterChange
      laf.autodetect = it
      WelcomeScreenEventCollector.logLafChanged(laf.getCurrentUIThemeLookAndFeel(), laf.autodetect)
    }
    ideFontProperty.afterChange(parentDisposable) {
      if (settings.fontSize2D == it) return@afterChange
      settings.fontFace = getIdeFontName()
      NotRoamableUiSettings.getInstance().overrideLafFonts = true
      WelcomeScreenEventCollector.logIdeFontChanged(settings.fontSize2D, it)
      settings.fontSize2D = it
      updateFontSettingsLater()
    }
    keymapProperty.afterChange(parentDisposable) {
      if (keymapManager.activeKeymap == it) return@afterChange
      WelcomeScreenEventCollector.logKeymapChanged(it)
      keymapManager.activeKeymap = it
    }
    adjustColorsProperty.afterChange(parentDisposable) {
      if (adjustColorsProperty.get() == (settings.colorBlindness != null)) return@afterChange
      WelcomeScreenEventCollector.logColorBlindnessChanged(adjustColorsProperty.get())
      updateColorBlindness()
    }
    colorBlindnessProperty.afterChange(parentDisposable) {
      updateColorBlindness()
    }

    val busConnection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateProperty(ideFontProperty) { getIdeFontSize() } })
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
      (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
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
    val component = panel {
      val autodetectSupportedPredicate = ComponentPredicate.fromValue(laf.autodetectSupported)
      val syncThemeAndEditorSchemePredicate = autodetectSupportedPredicate.and(
        ComponentPredicate.fromObservableProperty(syncThemeProperty, parentDisposable))

      header(IdeBundle.message("welcome.screen.color.theme.header"), true)
      row(IdeBundle.message("combobox.look.and.feel")) {
        val themeBuilder = comboBox(LafComboBoxModelWrapper { laf.lafComboBoxModel })
          .bindItem(lafProperty)
          .accessibleName(IdeBundle.message("welcome.screen.color.theme.header"))
        themeBuilder.component.isSwingPopup = false
        themeBuilder.component.renderer = laf.getLookAndFeelCellRenderer(themeBuilder.component)

        colorThemeComboBox = themeBuilder.component
        val checkBox = checkBox(IdeBundle.message("preferred.theme.autodetect.selector"))
        checkBox.bindSelected(syncThemeProperty)
          .applyToComponent {
            isOpaque = false
            isVisible = laf.autodetectSupported
          }
          .gap(RightGap.SMALL)

        themeBuilder.enabledIf(syncThemeAndEditorSchemePredicate.not())
        cell(laf.createSettingsToolbar())
          .visible(laf.autodetectSupported)

        lafConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener { source: LafManager? ->
          if (laf.autodetect != syncThemeProperty.get()) {
            checkBox.selected(laf.autodetect)
          }
        })
      }

      indent {
        val colorAndFontsOptions = ColorAndFontOptions().apply {
          setShouldChangeLafIfNecessary(false)
          setSchemesPanelFactory(object : SchemesPanelFactory {
            override fun createSchemesPanel(options: ColorAndFontOptions): SchemesPanel {
              return EditorSchemesPanel(options, true)
            }
          })
        }
        val editorSchemeCombo = colorAndFontsOptions.createComponent(true)
        editorSchemeCombo.isOpaque = false
        colorAndFontsOptions.reset()

        row {
          cell(editorSchemeCombo).onIsModified {
            colorAndFontsOptions.isModified
          }.onApply {
            colorAndFontsOptions.apply()
          }.onReset {
            colorAndFontsOptions.reset()
          }.enabledIf(syncThemeAndEditorSchemePredicate.not())

          syncThemeAndEditorSchemePredicate.addListener { isSyncOn ->
            if (isSyncOn) {
              colorAndFontsOptions.reset()
            }
          }
        }

        parentDisposable.whenDisposed {
          colorAndFontsOptions.disposeUIResources()
        }
      }

      header(IdeBundle.message("title.language.and.region"))
      LanguageAndRegionUi.createContent(this, propertyGraph, parentDisposable, lafConnection, EventSource.WELCOME_SCREEN)

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

    val scrollPane = JBScrollPane(component)
    scrollPane.border = JBUI.Borders.empty()
    return scrollPane
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
    return ColorBlindness.entries.filter { ColorBlindnessSupport.get(it) != null }
  }

  private fun getKeymaps(): List<Keymap> {
    return keymapManager.getKeymaps(KeymapSchemeManager.FILTER).sortedWith(keymapComparator)
  }
}