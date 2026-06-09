// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ide.core.customization.ProjectLifecycleUiCustomization
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test


class ReopenLastProjectGeneralSettingsTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @Test
  fun reopenLastProjectRespectsStoredSettingByDefault() {
    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = true))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isTrue()

    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = false))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isFalse()
  }

  @Test
  fun reopenLastProjectIsUserModifiableWhenCustomizationSetsDefaultOnCustomImplementation() {
    setMode(ProjectLifecycleUiCustomization.ReopenProjectsOnStartupMode.USER_CONTROLLABLE)

    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = false))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isFalse()

    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = true))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isTrue()
  }

  @Test
  fun reopenLastProjectIsForcedOnWhenCustomizationRequiresIt() {
    setMode(ProjectLifecycleUiCustomization.ReopenProjectsOnStartupMode.ALWAYS)
    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = false))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isTrue()
  }

  @Test
  fun reopenLastProjectIsForcedOffWhenCustomizationDisablesIt() {
    setMode(ProjectLifecycleUiCustomization.ReopenProjectsOnStartupMode.NEVER)
    GeneralSettings.getInstance().loadState(GeneralSettingsState(reopenLastProject = true))
    assertThat(GeneralSettings.getInstance().isReopenLastProject).isFalse()
  }

  private fun setMode(mode: ProjectLifecycleUiCustomization.ReopenProjectsOnStartupMode) {
    ApplicationManager.getApplication()
      .replaceService(ProjectLifecycleUiCustomization::class.java, object : ProjectLifecycleUiCustomization() {
        override val reopenProjectsOnStartupMode: ReopenProjectsOnStartupMode get() = mode
      }, disposableRule.disposable)
  }

}