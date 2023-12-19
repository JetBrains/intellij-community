// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import javax.swing.Icon

class WizardMockData : WizardProvider {
  override fun getWizardService(): WizardService {
    return WizardServiceTest()
  }
}

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
  override val plugins: List<WizardPlugin> = emptyList()
  override fun install(ids: List<String>): PluginImportProgress {
    return object : PluginImportProgress {
      override val progressMessage: IPropertyView<String?> = Property<String?>(null)

      override val progress: IOptPropertyView<Int> = OptProperty<Int>()
    }
  }
}


class TestKeymapService : KeymapService {
  override val maps: List<WizardKeymap>
    get() = emptyList()

  override fun chosen(id: String) {

  }

}

