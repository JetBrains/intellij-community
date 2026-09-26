// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LargestObjectWithOwnerTest {

  @Test
  fun `detects OOM report by histogram header on the first line`() {
    assertTrue(LargestObjectWithOwner.isOomReport(oomReport()))
    assertFalse(LargestObjectWithOwner.isOomReport("prefix\n==================== HISTOGRAM ===================="))
    assertFalse(LargestObjectWithOwner.isOomReport("not an OOM report"))
    assertFalse(LargestObjectWithOwner.isOomReport(""))
  }

  @Test
  fun `requires full OOM report`() {
    val exception = assertThrows(IllegalArgumentException::class.java) {
      LargestObjectWithOwner.find("not an OOM report")
    }

    assertEquals("The provided text is not a full OOM report.", exception.message)
  }

  @Test
  fun `finds largest object and extracts owner class from java frame root`() {
    val owner = LargestObjectWithOwner.find(oomReport())

    requireNotNull(owner)
    assertEquals("com.example.plugin.LeakingAction", owner.largestObject.className)
    assertNull(owner.descriptor)
    assertEquals("com.example.plugin.LeakingAction.actionPerformed", owner.largestObject.description)
    assertEquals(1_200_000_000L, owner.largestObject.sizeInBytes)
    assertEquals(
      "1.20GB          1 ROOT: Java Frame: com.example.plugin.LeakingAction.actionPerformed(LeakingAction.java:42)",
      owner.largestObject.rootPath.lineSequence().first(),
    )
  }

  @Test
  fun `returns null when largest object is below reporting threshold`() {
    assertNull(LargestObjectWithOwner.find(oomReport(largestObjectSize = "100MB")))
  }

  private fun oomReport(largestObjectSize: String = "1.20GB"): String {
    return """
      ==================== HISTOGRAM ====================
      Histogram. Top 50 by instance count [All-objects] [Only-strong-ref]:
          1: [    1/  16B] [    1/  16B] byte[]
      Total -        All:     1  16B 1 classes (Total instances: 1)
      Total - Strong-ref:     1  16B 1 classes (Total instances: 1)
      =================== HEAP SUMMARY ==================
      Analysis completed! Visited instances: 1, time: 1 ms
      ======== INSTANCES OF EACH NOMINATED CLASS ========
      Nominated classes:
       --> [1/16B] byte[]

      CLASS: byte[] (1 objects)
      Root 1:
      [    1/100%/  16B] $largestObjectSize          1   ROOT: Java Frame: com.example.plugin.LeakingAction.actionPerformed(LeakingAction.java:42)
      [    1/100%/  16B] $largestObjectSize          1   (root): com.example.plugin.LeakingPayload
      [    1/100%/  16B] $largestObjectSize          1 * value: byte[]
      =================== END ==================
    """.trimIndent()
  }
}
