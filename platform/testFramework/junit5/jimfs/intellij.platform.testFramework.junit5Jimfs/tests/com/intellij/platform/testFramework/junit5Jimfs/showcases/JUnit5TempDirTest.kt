// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5Jimfs.showcases

import com.intellij.platform.testFramework.junit5Jimfs.JimfsTempDir
import com.intellij.platform.testFramework.junit5Jimfs.JimfsTempDirFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

class JUnit5TempDirTest {
  @Test
  fun tempDir(@TempDir physicalTempDir: Path, @TempDir(factory = JimfsTempDirFactory::class) memoryTempDir: Path, @JimfsTempDir memoryTempDir2: Path) {
    assertTrue(physicalTempDir.let { it.exists() && it.isDirectory() }, "Directory must be on local fs")
    assertNotEquals(physicalTempDir.fileSystem::class, memoryTempDir.fileSystem::class, "Memory filesystem differs from physical")
    assertEquals(memoryTempDir.fileSystem::class, memoryTempDir2.fileSystem::class, "Annotation works as shortcut for the explicit provider")
    memoryTempDir.resolve("file2.txt").writeText("test2")
  }
}