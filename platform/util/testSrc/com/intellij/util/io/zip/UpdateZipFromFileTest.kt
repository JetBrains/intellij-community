// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip

import com.intellij.util.io.directoryContent
import org.junit.Test
import java.io.File

/**
 * @author nik
 */
class UpdateZipFromFileTest {
  @Test
  fun `add entry`() {
    val dir = directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
      }
      file("b.txt", text = "b")
    }.generateInTempDir()

    val zip = JBZipFile(File(dir, "a.zip"))
    zip.getOrCreateEntry("b.txt").setDataFromFile(File(dir, "b.txt"))
    zip.close()

    directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
        file("b.txt", text = "b")
      }
      file("b.txt", text = "b")
    }
  }

  @Test
  fun `replace entry`() {
    val dir = directoryContent {
      zip("a.zip") {
        file("a.txt", text = "a")
      }
      file("b.txt", text = "b")
    }.generateInTempDir()

    val zip = JBZipFile(File(dir, "a.zip"))
    zip.getOrCreateEntry("a.txt").setDataFromFile(File(dir, "b.txt"))
    zip.close()

    directoryContent {
      zip("a.zip") {
        file("a.txt", text = "b")
      }
      file("b.txt", text = "b")
    }
  }
}