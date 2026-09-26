// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.serviceContainer.servicePreloadingAllowListForNonCorePlugin
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
open class AllowedPreloadServicesTest {
  private val allowed: Set<String> = servicePreloadingAllowListForNonCorePlugin

  @Test
  fun `allow list for preloaded services matches declared services`() {
    assertNotNull(ApplicationManager.getApplication())

    val failedChecks = mutableSetOf<String>()

    val pluginModules = PluginManagerCore.getPluginSet().getEnabledModules()
    for (descriptor in pluginModules) {
      val services = descriptor.appContainerDescriptor.services +
                     descriptor.projectContainerDescriptor.services

      for (service in services) {
        if (descriptor.pluginId != PluginManagerCore.CORE_ID
            && service.preload != ServiceDescriptor.PreloadMode.FALSE) {
          if (!allowed.contains(service.serviceImplementation)) {
            failedChecks.add(service.serviceImplementation)
          }
        }
      }
    }

    assertTrue(
      failedChecks.isEmpty(),
      "Services are not allowed to use `preload` in plugin.xml registration: \n${failedChecks.joinToString("\n")}. \n\n" +
      "Plugins may not use preloading, use <postStartupActivity> ProjectActivity, lazy MessageBus topics or specific extensions instead."
    )
  }
}