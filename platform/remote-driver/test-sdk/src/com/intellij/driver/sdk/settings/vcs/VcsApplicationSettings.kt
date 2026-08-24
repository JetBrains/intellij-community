package com.intellij.driver.sdk.settings.vcs

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.vcs.VcsApplicationSettings", rdTarget = RdTarget.BACKEND)
interface VcsApplicationSettings {
  fun isCreateChangeListsAutomatically(): Boolean

  fun setCreateChangeListsAutomatically(value: Boolean)

  companion object {
    context(driver: Driver)
    fun getInstance(): VcsApplicationSettings = driver.service(VcsApplicationSettings::class)
  }
}