// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.setEelDescriptor
import com.intellij.platform.eel.provider.utils.EelProjectUtils
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.delete
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
internal class EelProjectUtilsTest {
  private val project = projectFixture()

  @Test
  fun `local project creates temporary entries locally`() {
    val project = project.get()

    assertTrue(EelProjectUtils.isProjectLocal(project))
    useTemporaryEntry(EelProjectUtils.createTemporaryFile(project, "local", ".txt")) { file ->
      assertTrue(Files.isRegularFile(file))
      assertEquals(LocalEelDescriptor, file.getEelDescriptor())
    }
    useTemporaryEntry(EelProjectUtils.createTemporaryDirectory(project, "local")) { dir ->
      assertTrue(Files.isDirectory(dir))
      assertEquals(LocalEelDescriptor, dir.getEelDescriptor())
    }
  }

  /**
   * The project store is local, but the descriptor set via [setEelDescriptor] points elsewhere (like in Draft over SSH).
   * The helpers must go to the descriptor's machine instead of the local file system.
   * The descriptor has no machine, so reaching it fails, which shows that nothing is created locally.
   */
  @OptIn(EelDelicateApi::class)
  @Test
  fun `project with local store and remote descriptor creates temporary entries on descriptor machine`() {
    val project = project.get()
    val descriptor = UnreachableEelDescriptor()
    assertEquals(LocalEelDescriptor, Path.of(project.projectFilePath!!).getEelDescriptor())

    project.setEelDescriptor(descriptor)

    assertFalse(EelProjectUtils.isProjectLocal(project))
    assertNoMachineFor(descriptor, assertThrows<IllegalStateException> {
      EelProjectUtils.createTemporaryFile(project, "remote", ".txt")
    })
    assertNoMachineFor(descriptor, assertThrows<IllegalStateException> {
      EelProjectUtils.createTemporaryDirectory(project, "remote")
    })
  }

  private fun assertNoMachineFor(descriptor: EelDescriptor, error: IllegalStateException) {
    assertTrue(error.message.orEmpty().contains(descriptor.name), error.message)
  }

  private fun useTemporaryEntry(path: Path, block: (Path) -> Unit) {
    try {
      block(path)
    }
    finally {
      path.delete(true)
    }
  }

  private class UnreachableEelDescriptor : EelDescriptor {
    override val name: String = "unreachable test descriptor"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }
}
