// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.RepeatedTest

@TestApplication
internal class EelTestDescriptorNeverBrokenTest {
  private companion object {
    val key = Key.create<MutableList<EelDescriptor>>("TestEelDescriptorNeverDisposedTest")
  }

  private val eelFixture = eelFixture()

  @RepeatedTest(3)
  fun testEnsureNeverDisposed(): Unit = runBlocking {
    val descriptors = ApplicationManager.getApplication().getOrCreateUserDataUnsafe(key) {
      ArrayList()
    }
    descriptors.add(eelFixture.get().eelDescriptor)
    for (descriptor in descriptors) {
      // fixture is disposed, but descriptor isn't
      descriptor.toEelApi().platform
    }
  }
}
