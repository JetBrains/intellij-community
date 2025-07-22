package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.ide.GeneralSettings")
interface GeneralSettingsRef {
  fun setConfirmOpenNewProject(mode: Int)
}

@Remote("com.intellij.openapi.options.advanced.AdvancedSettings")
interface AdvancedSettingsRef {
  fun getBoolean(id: String): Boolean
  fun setBoolean(id: String, value: Boolean)
  fun getDefaultBoolean(id: String): Boolean
}

fun Driver.setOpenNewProjectsInSameWindow() {
  service<GeneralSettingsRef>(RdTarget.BACKEND).setConfirmOpenNewProject(1)
}

fun Driver.setOpenNewProjectsInNewWindow() {
  service<GeneralSettingsRef>(RdTarget.BACKEND).setConfirmOpenNewProject(0)
}

fun Driver.advancedSettings(rdTarget: RdTarget = RdTarget.DEFAULT): AdvancedSettingsRef {
  return service<AdvancedSettingsRef>(rdTarget)
}

fun Driver.setAdvancedSetting(id: String, value: Boolean, rdTarget: RdTarget = RdTarget.DEFAULT) {
  advancedSettings(rdTarget).setBoolean(id, value)
}
