// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.zip

import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.junit.Test

class UpdateZipFromFileTest {
  @Test fun `add entry`() {
    val dir = directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
      }
      file("b.txt", text = "b")
    }.generateInTempDir()

    val zip = JBZipFile(dir.resolve("a.zip"), false)
    zip.use { zip.getOrCreateEntry("b.txt").setDataFromPath(dir.resolve("b.txt")) }

    dir.assertMatches(directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
        file("b.txt", text = "b")
      }
      file("b.txt", text = "b")
    })
  }

  @Test fun `replace entry`() {
    val dir = directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
      }
      file("b.txt", text = "b")
    }.generateInTempDir()

    val zip = JBZipFile(dir.resolve("a.zip"), false)
    zip.use { zip.getOrCreateEntry("a.txt").setDataFromPath(dir.resolve("b.txt")) }

    dir.assertMatches(directoryContent {
      zip("a.zip") {
        file("a.txt", text = "b")
      }
      file("b.txt", text = "b")
    })
  }
}
