// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application.extensions

import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.testFramework.common.runAll
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class ExternalSystemListenerLeakTracker : AfterEachCallback {

  override fun afterEach(context: ExtensionContext) {
    runAll(
      { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() }
    )
  }
}