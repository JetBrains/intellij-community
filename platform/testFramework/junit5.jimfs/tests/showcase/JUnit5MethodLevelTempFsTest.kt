// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.jimfs.showcase

import com.intellij.platform.testFramework.junit5.jimfs.jimFsFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem

/**
 * [com.google.common.jimfs.Jimfs] created before and [FileSystem.close]d after the test
 */
@TestApplication
class JUnit5MethodLevelTempFsTest {

  companion object {

    @AfterAll
    @JvmStatic
    fun ensureFsClosed() {
      assertFalse(ourFs.isOpen) // FS must be closed as it is JimFS
    }

    private lateinit var ourFs: FileSystem
  }

  private val fsFixture = jimFsFixture()

  @Test
  fun test() {
    val fs = fsFixture.get()
    assertTrue(fs.isOpen)
    ourFs = fs
    fs.rootDirectories.first().resolve("file").write("D")
  }
}
