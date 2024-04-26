// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

class WizardServiceTest : StartupWizardService {
  override val isActive = true
  override val shouldClose = Signal<Unit>()

  override fun getKeymapService(): KeymapService {
    return TestKeymapService()
  }

  override fun getThemeService(): ThemeService {
    return ThemeServiceImpl()
  }

  override fun getPluginService(): PluginService {
    return PluginServiceImpl()
  }

  override fun onEnter() {}
  override fun onCancel() {}
  override fun onExit() {}
}

class ThemeServiceImpl : ThemeService {

  private var vs: @NlsSafe String = "Visual Studio"
  private var vsa: @NlsSafe String = "Visual Assist"
  private var rider: @NlsSafe String = "Rider"
  private var IDEA: @NlsSafe String = "IDEA"

  private val map = mapOf(
    vs to WizardScheme(vs, vs, IconLoader.getIcon("wizardPreviews_stub/vs.png", this.javaClass), JBColor(0xFFFFFF, 0x1E1E1E)),
    rider to WizardScheme(rider, rider, IconLoader.getIcon("wizardPreviews_stub/rider.png", this.javaClass), JBColor(0xFFFFFF, 0x262626)),
    vsa to WizardScheme(vsa, vsa, IconLoader.getIcon("wizardPreviews_stub/vs.png", this.javaClass), JBColor(0xFFFFFF, 0x1E1E1E)),
    IDEA to WizardScheme(IDEA, IDEA, IconLoader.getIcon("wizardPreviews_stub/ij.png", this.javaClass), JBColor(0xFFFFFF, 0x2B2B2B))
  )

  override var currentTheme: ThemeService.Theme
    get() = if(StartupUiUtil.isDarkTheme) ThemeService.Theme.Dark else ThemeService.Theme.Light

    set(value) {
      val lm = LafManager.getInstance()

      val laf = if(value.isDark)
        lm.defaultDarkLaf else LafManager.getInstance().defaultLightLaf

      laf?.let {
        lm.apply {
          currentUIThemeLookAndFeel = it
          updateUI()
          repaintUI()
        }
      }
    }

  override val schemesList: List<WizardScheme> = map.values.toList()

  override fun onStepEnter(isForwardDirection: Boolean) {}

  override fun updateScheme(schemeId: String) {

  }
}

class PluginServiceImpl : PluginService {
  private val pl1 = WizardPluginImpl(AllIcons.Plugins.PluginLogo, "Python Community Edition",
                                 "The Python plugin provides smart editing for Python scripts. The feature set of the plugin corresponds to PyCharm IDE Community Edition")
  private val p2 = WizardPluginImpl(AllIcons.Plugins.PluginLogoDisabled, "IdeaVim", "Emulates Vim editor")
  private val p3 = WizardPluginImpl(AllIcons.TransferSettings.Vscode, "Ideolog",
                            "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code.")
  private val p4 = WizardPluginImpl(AllIcons.TransferSettings.Keymap, "Ideolog",
                            "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code.")
  private val listOf = listOf(
    pl1,
    WizardPluginImpl(AllIcons.TransferSettings.Vscode, "Ideolog"),
    p2,
    WizardPluginImpl(AllIcons.TransferSettings.RecentProjects, "Heap Allocation Viewer",
                     "Highlights local object allocations, boxing, delegates and closure creations points"),
    WizardPluginImpl(AllIcons.TransferSettings.Vscode, "Ideolog",
                     "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code."),
    WizardPluginImpl(AllIcons.TransferSettings.Vsmac, "Ideolog", ""),
    WizardPluginImpl(AllIcons.Plugins.PluginLogoDisabled, "IdeaVim", "Emulates Vim editor"),
    WizardPluginImpl(AllIcons.TransferSettings.RecentProjects, "Heap Allocation Viewer",
                     "Highlights local object allocations, boxing, delegates and closure creations points"),
    p3,
    WizardPluginImpl(AllIcons.TransferSettings.Vsmac, "Ideolog"),
    p4,
    WizardPluginImpl(AllIcons.TransferSettings.PluginsAndFeatures, "Ideolog",
                     "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code."),
    WizardPluginImpl(AllIcons.TransferSettings.Settings, "Ideolog",
                     "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code."),
    WizardPluginImpl(AllIcons.TransferSettings.Vscode, "Ideolog",
                     "An interactive viewer for log files with customizible highlighting, filtration and navigation to source code."),

  )

  private val listOf1: List<WizardPlugin> = listOf(
    WizardPluginImpl(AllIcons.Plugins.PluginLogo, "Python Community Edition", "The Python plugin provides smart editing for Python scripts. The feature set of the plugin corresponds to PyCharm IDE Community Edition"),
    WizardPluginImpl(AllIcons.Plugins.PluginLogoDisabled, "IdeaVim", "Emulates Vim editor"),
    WizardPluginImpl(AllIcons.TransferSettings.RecentProjects, "Heap Allocation Viewer", "Highlights local object allocations, boxing, delegates and closure creations points"),
  )

  override val plugins: List<WizardPlugin> = listOf

  override fun onStepEnter() {}

  override fun install(lifetime: Lifetime, ids: List<String>): PluginImportProgress = TestPluginImportProgress(lifetime)
  override fun skipPlugins() {

  }
}

class TestPluginImportProgress(lifetime: Lifetime) : TestImportProgress(lifetime), PluginImportProgress {
  private val iconList = listOf(AllIcons.Plugins.PluginLogo,
                                AllIcons.Plugins.PluginLogoDisabled,
                                AllIcons.TransferSettings.RecentProjects,
                                AllIcons.TransferSettings.Vscode,
                                AllIcons.TransferSettings.Settings,
                                AllIcons.TransferSettings.PluginsAndFeatures)

  override val icon = Property(AllIcons.Plugins.PluginLogo)

  private var index = 0
  init {
    lifetime.launch {
      launch(Dispatchers.Default) {
        while (true) {
          index = if (index < iconList.size - 1) index + 1 else 0
          icon.set(
            iconList[index]
          )
          delay(300L)
        }
      }
    }
  }
}

class WizardPluginImpl(override val icon: Icon,
                       override val name: String,
                       override val description: String? = null,
                       override val id: String = UUID.randomUUID().toString()) : WizardPlugin {
}

@Suppress("HardCodedStringLiteral")
class TestKeymapService : KeymapService {
  override val keymaps: List<WizardKeymap> =
    listOf(TestWizardKeymap(UUID.randomUUID().toString(), "Visual Studio",
                            "Visual Studio Visual Studio Visual Studio"),

           TestWizardKeymap(UUID.randomUUID().toString(), "Visual Assist",
                            "Visual Studio Visual Studio Visual Studio"),
           TestWizardKeymap(UUID.randomUUID().toString(), "JetBrains IDE",
                            "Visual Studio Visual Studio Visual Studio"),
           TestWizardKeymap(UUID.randomUUID().toString(), "VS Code",
                            "Visual Studio Visual Studio Visual Studio")
           )

  override val shortcuts: List<Shortcut> = listOf(Shortcut(UUID.randomUUID().toString(), "Search"),
  Shortcut(UUID.randomUUID().toString(), "Debug"),
  Shortcut(UUID.randomUUID().toString(), "Find Usages"),
  Shortcut(UUID.randomUUID().toString(), "Extend Selection"),
  Shortcut(UUID.randomUUID().toString(), "Build Solution"))

  override fun onStepEnter(isForwardDirection: Boolean) {}

  override fun chosen(id: String) {

  }
}

class TestWizardKeymap(override val id: String,
                       override val name: String,
                       override val description: @Nls String) : WizardKeymap {

  private val shortCuts = listOf("Shift+Shift", "F12", "Alt+Shift+F12", "Alt+Shift+F1", "F1", "Ctrl+Shift+B")
  override fun getShortcutValue(id: String): String {
    return shortCuts.random()
  }
}
