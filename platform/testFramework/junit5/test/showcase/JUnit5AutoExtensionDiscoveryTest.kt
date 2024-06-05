// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.engine.Constants

class JUnit5AutoExtensionDiscoveryTest {

  /**
   * @see com.intellij.testFramework.junit5.impl.JUnit5TestEnvironmentInitializer.enableAutoExtensionDiscovery
   */
  @Test
  fun `auto discovery is turned on`() {
    assertEquals(System.getProperty(Constants.EXTENSIONS_AUTODETECTION_ENABLED_PROPERTY_NAME), "true")
    assertTrue(MetaAutoEnabledExtension.wasLoaded)
  }

  // An extension to test auto-discovery
  class MetaAutoEnabledExtension : BeforeAllCallback {

    companion object {

      var wasLoaded: Boolean = false
        private set
    }

    override fun beforeAll(context: ExtensionContext?) {
      wasLoaded = true
    }
  }
}
