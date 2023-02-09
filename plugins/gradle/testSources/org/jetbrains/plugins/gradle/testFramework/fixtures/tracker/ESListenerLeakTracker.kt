// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture

class ESListenerLeakTracker : IdeaTestFixture {

  override fun setUp() = Unit

  override fun tearDown() {
    runAll(
      { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() }
    )
  }
}