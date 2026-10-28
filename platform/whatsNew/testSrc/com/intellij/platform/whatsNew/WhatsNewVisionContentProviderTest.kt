// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

@TestApplication
class WhatsNewVisionContentProviderTest {
  // IJPL-217529
  @Test
  fun testLegacyResourceNaming() {
    val provider = object : WhatsNewInVisionContentProvider() {
      override val baseResourcePathInClassLoader = "com/intellij/platform/whatsNew/idea/eap"
      fun extractResourceNames() = getVisionJsonResourceNames()
    }
    
    val resourceNames = provider.extractResourceNames()
    
    val contentSource = ResourceContentSource(this::class.java.classLoader, resourceNames)
    runBlocking {
      Assertions.assertTrue(contentSource.checkAvailability(), "Resource availability check failed")
      Assertions.assertTrue(contentSource.openStream() != null, "Resource stream opening failed")
    }
  }

  @Test
  fun testMediaNextToTestJson(@TempDir dir: Path) {
    val json = dir.resolve("vision.json").createFile()
    dir.resolve("static/image.png").createParentDirectories().writeText("png")
    val oldValue = System.setProperty("intellij.whats.new.vision.json", json.toString())
    try {
      val provider = WhatsNewInVisionContentProvider()
      runBlocking {
        Assertions.assertTrue(provider.getWhatsNewResource("static/image.png").checkAvailability())
        Assertions.assertFalse(provider.getWhatsNewResource("static/missing.png").checkAvailability())
      }
    }
    finally {
      if (oldValue == null) System.clearProperty("intellij.whats.new.vision.json") else System.setProperty("intellij.whats.new.vision.json", oldValue)
    }
  }
}