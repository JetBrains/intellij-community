// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

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

}

class PluginServiceImpl : PluginService {

}


class TestKeymapService : KeymapService {
  override val maps: List<WizardKeymap>
    get() = emptyList()

  override fun chosen(id: String) {

  }

}

class TestThemeService : ThemeService {}

