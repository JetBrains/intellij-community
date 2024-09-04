package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("com.intellij.ide.GeneralSettings")
interface GeneralSettingsRef {
  fun setConfirmOpenNewProject(mode: Int)
}

@Remote("com.intellij.openapi.options.advanced.AdvancedSettings")
interface AdvancedSettingsRef {
  fun setBoolean(id: String, value: Boolean)
}

fun Driver.setOpenNewProjectsInSameWindow() {
  service(GeneralSettingsRef::class).setConfirmOpenNewProject(1)
}

fun Driver.setOpenNewProjectsInNewWindow() {
  service(GeneralSettingsRef::class).setConfirmOpenNewProject(0)
}

fun Driver.setAdvancedSetting(id: String, value: Boolean) {
  service(AdvancedSettingsRef::class).setBoolean(id, value)
}
