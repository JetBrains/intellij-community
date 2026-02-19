package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.ide.GeneralSettings")
interface GeneralSettingsRef {
  fun setConfirmOpenNewProject(mode: Int)
  fun setConfirmExit(value: Boolean)
  fun setProcessCloseConfirmation(value: ProcessCloseConfirmationRef)
}

@Remote("com.intellij.ide.ProcessCloseConfirmation")
interface ProcessCloseConfirmationRef {
  fun valueOf(name: String): ProcessCloseConfirmationRef
}

@Remote("com.intellij.openapi.options.advanced.AdvancedSettings")
interface AdvancedSettingsRef {
  fun getBoolean(id: String): Boolean
  fun setBoolean(id: String, value: Boolean)
  fun getDefaultBoolean(id: String): Boolean
}

@Remote("com.intellij.openapi.updateSettings.impl.UpdateSettings")
interface UpdateSettings {
  fun setThirdPartyPluginsAllowed(allowed: Boolean)
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

fun Driver.setThirdPartyPluginsAllowed(allowed: Boolean) {
  service<UpdateSettings>().setThirdPartyPluginsAllowed(allowed)
}

fun Driver.turnOffConfirmExit() {
  val settings = service<GeneralSettingsRef>(RdTarget.BACKEND)
  settings.setConfirmExit(false)
  settings.setProcessCloseConfirmation(utility<ProcessCloseConfirmationRef>(RdTarget.BACKEND).valueOf("TERMINATE"))
}
