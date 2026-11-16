// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.unregisterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
internal class MinimapServiceDisposalTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `dispose does not require registered settings`(unregisterSettings: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val application = ApplicationManager.getApplication()
      val settings = MinimapSettings()
      val replacement = Disposer.newDisposable()
      try {
        application.replaceService(MinimapSettings::class.java, settings, replacement)
        val service = MinimapService(this)
        if (unregisterSettings) {
          application.unregisterService(MinimapSettings::class.java)
        }
        try {
          if (unregisterSettings) {
            assertThat(application.getServiceIfCreated(MinimapSettings::class.java)).isNull()
          }
          service.dispose()
        }
        finally {
          if (unregisterSettings) {
            application.registerServiceInstance(MinimapSettings::class.java, settings)
          }
          service.dispose()
        }
      }
      finally {
        Disposer.dispose(replacement)
      }
    }
  }
}
