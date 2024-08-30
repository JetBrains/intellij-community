package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("com.intellij.ide.GeneralSettings")
interface GeneralSettingsRef {
  fun setConfirmOpenNewProject(mode: Int)
}

fun Driver.setOpenNewProjectsInSameWindow() {
  service(GeneralSettingsRef::class).setConfirmOpenNewProject(1)
}

fun Driver.setOpenNewProjectsInNewWindow() {
  service(GeneralSettingsRef::class).setConfirmOpenNewProject(0)
}
