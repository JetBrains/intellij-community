// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase

class AdvancedSettingsTest : LightPlatformTestCase() {


  @TestFor(issues = ["IJPL-172170"])
  fun testUnknownSettingPresent() {

    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    val state = AdvancedSettingsImpl.AdvancedSettingsState()
    state.settings = mutableMapOf("bean.id" to "bean.value")
    advancedSettings.loadState(state)
    assertTrue(advancedSettings.state.settings.containsKey("bean.id"))
  }

  @TestFor(issues = ["IJPL-172170"])
  fun testUnknownLoadedAfterEPEnabled() {
    val advancedSettingsService = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    val state = AdvancedSettingsImpl.AdvancedSettingsState()
    state.settings = mutableMapOf("bean.id" to "bean.value")
    advancedSettingsService.loadState(state)

    val meinBean = AdvancedSettingBean().apply {
      id = "bean.id"
      groupKey = "mein.group"
      defaultValue = "MEGA-DEFAULT"
    }
    val disposable = Disposable {
      assertTrue(advancedSettingsService.state.settings.containsKey("bean.id"))
    }
    AdvancedSettingBean.EP_NAME.point.registerExtension(meinBean, disposable)
    assertTrue(AdvancedSettings.getString("bean.id") == "bean.value")
    assertTrue(advancedSettingsService.state.settings.containsKey("bean.id"))
    Disposer.dispose(disposable)
  }

  fun testProductReplacesDefault() {
    AdvancedSettingDefaultBean.EP_NAME.point.registerExtension(AdvancedSettingDefaultBean().apply {
      id = "replaced.default.id"
      defaultValue = "false"
    }, testRootDisposable)
    AdvancedSettingBean.EP_NAME.point.registerExtension(AdvancedSettingBean().apply {
      id = "replaced.default.id"
      defaultValue = "true"
    }, testRootDisposable)

    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    assertFalse(AdvancedSettings.getBoolean("replaced.default.id"))
    assertEquals(false, AdvancedSettings.getDefaultBoolean("replaced.default.id"))

    AdvancedSettings.setBoolean("replaced.default.id", true)
    assertTrue(advancedSettings.isNonDefault("replaced.default.id"))
    AdvancedSettings.setBoolean("replaced.default.id", false)
    assertFalse(advancedSettings.isNonDefault("replaced.default.id"))
  }
}