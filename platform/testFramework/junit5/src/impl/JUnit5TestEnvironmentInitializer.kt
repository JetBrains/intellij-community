// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.initializeTestEnvironment
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.engine.Constants
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

@TestOnly
internal class JUnit5TestEnvironmentInitializer : LauncherSessionListener {

  override fun launcherSessionOpened(session: LauncherSession) {
    enableAutoExtensionDiscovery()
    initializeTestEnvironment()
  }

  /**
   * Turns of extensions listed in META-INF/services/org.junit.jupiter.api.extension.Extension in all tests.
   *
   * @see com.intellij.testFramework.junit5.showcase.JUnit5AutoExtensionDiscoveryTest
   */
  private fun enableAutoExtensionDiscovery() {
    System.setProperty(Constants.EXTENSIONS_AUTODETECTION_ENABLED_PROPERTY_NAME, "true")
  }
}
