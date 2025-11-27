// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class WhatsNewVisionContentProviderTest {
  // IJPL-217529
  @Test
  fun testLegacyResourceNaming() {
    val provider = object : WhatsNewInVisionContentProvider() {
      fun extractResourceNames() = getVisionJsonResourceNames()
    }
    
    val resourceNames = provider.extractResourceNames()
    
    val contentSource = ResourceContentSource(this::class.java.classLoader, resourceNames)
    runBlocking {
      Assertions.assertTrue(contentSource.checkAvailability(), "Resource availability check failed")
      Assertions.assertTrue(contentSource.openStream() != null, "Resource stream opening failed")
    }
  }
}