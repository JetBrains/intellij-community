// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.gotoByName.FindActionSearchableOptionsFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class AdvancedSettingsFindActionOptionsFilterTest {
  @Test
  fun `non-advanced settings descriptions are available`() {
    assertThat(isAvailable(optionDescription(configurableId = "editor.preferences", hit = "Stale Advanced Setting"))).isTrue()
  }

  @Test
  @TestFor(issues = ["IJPL-246255"])
  fun `stale advanced settings option is filtered out`() {
    assertThat(isAvailable(advancedSettingsDescription("Stale Advanced Setting"))).isFalse()
  }

  @Test
  fun `registered visible advanced settings option is available`(@TestDisposable disposable: Disposable) {
    val setting = advancedSetting("ijpl.246255.visible.setting")
    AdvancedSettingBean.EP_NAME.point.registerExtension(setting, disposable)

    assertThat(isAvailable(advancedSettingsDescription(setting.title()))).isTrue()
  }

  @Test
  fun `registered visible advanced settings description is available`(@TestDisposable disposable: Disposable) {
    val setting = advancedSetting("ijpl.246255.setting.with.description").apply {
      bundle = ApplicationBundle.BUNDLE
      titleKey = "title.advanced.settings"
      descriptionKey = "search.advanced.settings.nothing.found"
    }
    AdvancedSettingBean.EP_NAME.point.registerExtension(setting, disposable)

    assertThat(isAvailable(advancedSettingsDescription(setting.description()!!))).isTrue()
  }

  @Test
  fun `registered enum advanced settings value is available`(@TestDisposable disposable: Disposable) {
    val setting = advancedSetting("ijpl.246255.enum.setting").apply {
      defaultValue = TestEnum.Alpha.name
      enumClass = TestEnum::class.java.name
    }
    AdvancedSettingBean.EP_NAME.point.registerExtension(setting, disposable)

    assertThat(isAvailable(advancedSettingsDescription(TestEnum.Beta.toString()))).isTrue()
  }

  @Test
  fun `registered hidden advanced settings option is filtered out`(@TestDisposable disposable: Disposable) {
    val setting = advancedSetting("ijpl.246255.hidden.setting").apply {
      visible = false
    }
    AdvancedSettingBean.EP_NAME.point.registerExtension(setting, disposable)

    assertThat(isAvailable(advancedSettingsDescription(setting.title()))).isFalse()
  }

  private fun isAvailable(description: OptionDescription): Boolean {
    return FindActionSearchableOptionsFilter.EP_NAME.extensionList.all { it.isAvailable(description) }
  }

  private fun advancedSettingsDescription(hit: String): OptionDescription {
    return optionDescription(configurableId = "advanced.settings", hit = hit)
  }

  private fun optionDescription(configurableId: String, hit: String): OptionDescription {
    return OptionDescription(_option = null, configurableId = configurableId, hit = hit, path = null)
  }

  private fun advancedSetting(id: String): AdvancedSettingBean {
    return AdvancedSettingBean().apply {
      this.id = id
      defaultValue = "false"
    }
  }

  private enum class TestEnum {
    Alpha,
    Beta;

    override fun toString(): String = "Option $name"
  }
}
