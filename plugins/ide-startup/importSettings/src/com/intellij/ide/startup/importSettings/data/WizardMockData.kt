// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.icons.AllIcons
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.*
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

class WizardServiceTest : WizardService {
  override fun getKeymapService(): KeymapService {
    return TestKeymapService()
  }

  override fun getThemeService(): ThemeService {
    return ThemeServiceImpl()
  }

  override fun getPluginService(): PluginService {
    return PluginServiceImpl()
  }


}

class ThemeServiceImpl : ThemeService {
  override val themeList: List<WizardTheme>
    get() = emptyList()

  override fun getEditorImageById(themeId: String, isDark: Boolean): Icon? {
    return null
  }

  override fun chosen(themeId: String, isDark: Boolean) {

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
    WizardPluginImpl(AllIcons.TransferSettings.Vscode, "Ideolog", ""),
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
    WizardPluginImpl(AllIcons.TransferSettings.Vsmac, "Ideolog", ""),
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

  override fun install(ids: List<String>): PluginImportProgress = TestPluginImportProgress(Lifetime.Eternal)
}

class TestPluginImportProgress(lifetime: Lifetime) : TestImportProgress(lifetime), PluginImportProgress {
  override val complete: Signal<PluginInstallationResult> = Signal()
}

class WizardPluginImpl(override val icon: Icon,
                       override val name: String,
                       override val description: String,
                       override val id: String = UUID.randomUUID().toString(),
                       override val state: IProperty<WizardPlugin.State> = Property(WizardPlugin.State.UNCHECKED)) : WizardPlugin {

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

  override fun chosen(id: String) {

  }
}

class TestWizardKeymap(override val id: String,
                       override val name: String,
                       override val description: @Nls String) : WizardKeymap {

  private val shortCuts = listOf("Shift+Shift", "Alt+Shift+F", "Alt+Shift+F12", "F12", "F1")
  override fun getShortcutValue(id: String): String {
    return shortCuts.random()
  }

}

