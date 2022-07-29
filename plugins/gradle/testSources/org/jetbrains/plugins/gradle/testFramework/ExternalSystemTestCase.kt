// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl.Companion.assertListenersReleased
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl.Companion.cleanupListeners
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BareTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

abstract class ExternalSystemTestCase {
  companion object {
    private lateinit var bareTestFixture: BareTestFixture
    private lateinit var virtualFilePointerTracker: VirtualFilePointerTracker

    @JvmStatic
    @BeforeAll
    fun setupTestCase() {
      bareTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()
      bareTestFixture.setUp()

      virtualFilePointerTracker = VirtualFilePointerTracker()
    }

    @JvmStatic
    @AfterAll
    fun tearDownTestCase() {
      runAll(
        { assertListenersReleased() },
        { cleanupListeners() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
        { bareTestFixture.tearDown() },
      )
    }
  }
}