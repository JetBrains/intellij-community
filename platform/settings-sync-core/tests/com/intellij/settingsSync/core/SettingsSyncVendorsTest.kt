@file:OptIn(IntellijInternalApi::class)

package com.intellij.settingsSync.core

import com.intellij.ide.plugins.PluginManagerCore.VENDOR_JETBRAINS
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorBean
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.getAvailableSyncProviders
import com.intellij.settingsSync.core.communicator.getSyncProviderPoint
import com.intellij.testFramework.UsefulTestCase.assertSize
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SettingSyncVendorsTest : SettingsSyncTestBase() {
  @BeforeEach
  fun setupProviders() {
    val providerPoint = getSyncProviderPoint()

    providerPoint.registerExtension(object : SettingsSyncCommunicatorBean() {
      init {
        this.pluginDescriptor = DefaultPluginDescriptor(
          PluginId.getId("com.intellij.allowed.sync.provider"),
          SettingsSyncTestBase::class.java.getClassLoader(),
          VENDOR_JETBRAINS
        )
      }

      override fun createInstance(
        componentManager: ComponentManager,
        pluginDescriptor: PluginDescriptor,
      ): SettingsSyncCommunicatorProvider = MockCommunicatorProvider(
        remoteCommunicator,
        authService,
        "ALLOWED_PROVIDER"
      )
    }, disposable)

    providerPoint.registerExtension(object : SettingsSyncCommunicatorBean() {
      init {
        this.pluginDescriptor = DefaultPluginDescriptor(
          PluginId.getId("com.intellij.3rd.party.provider"),
          SettingsSyncTestBase::class.java.getClassLoader(),
          "YourCompany"
        )
      }

      override fun createInstance(
        componentManager: ComponentManager,
        pluginDescriptor: PluginDescriptor,
      ): SettingsSyncCommunicatorProvider = MockCommunicatorProvider(
        remoteCommunicator,
        authService,
        "UNAUTHORIZED_PROVIDER"
      )
    }, disposable)
  }

  @Test
  fun testUnauthorizedProviderIsExcluded() {
    val extensions = getSyncProviderPoint().extensionList
    assertSize(3, extensions) // precondition, everything registered

    val availableSyncProviders = getAvailableSyncProviders()
    assertSize(2, availableSyncProviders)

    val codes = availableSyncProviders.map { it.providerCode }

    assertTrue(codes.contains("ALLOWED_PROVIDER"), "ALLOWED_PROVIDER must be present in the list")
    assertFalse(codes.contains("UNAUTHORIZED_PROVIDER"), "UNAUTHORIZED_PROVIDER must be absent from the list")
  }
}
