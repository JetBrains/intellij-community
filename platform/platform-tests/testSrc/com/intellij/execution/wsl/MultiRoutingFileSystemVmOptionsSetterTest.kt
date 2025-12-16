// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.platform.ide.bootstrap.eel.MultiRoutingFileSystemVmOptionsSetter
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class MultiRoutingFileSystemVmOptionsSetterTest {
  private infix fun Collection<Pair<String, String?>>.shouldMatch(other: Collection<String>) {
    assertThat(other)
      .withFailMessage("Improving test readability: lists should be sorted")
      .isEqualTo(other.sorted())

    assertThat(sortedBy { it.first }.joinToString("\n") { (k, v) -> "$k$v" })
      .isEqualTo(other.joinToString("\n"))
  }

  @Test
  fun `enable when no options set`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = true, forceProductionOptions = true, isEnabledByDefault = false, vmOptionsReader(""))
    changedOptions shouldMatch listOf(
      "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
      "-Dwsl.use.remote.agent.for.nio.filesystem=true",
    )
  }

  @Test
  fun `enable when no options set in unit test mode`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = true, forceProductionOptions = false, isEnabledByDefault = false, vmOptionsReader(""))
    changedOptions shouldMatch listOf(
      "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
      "-Dwsl.use.remote.agent.for.nio.filesystem=true",
      "-Xbootclasspath/a:out/classes/production/intellij.platform.core.nio.fs",
    )
  }

  @Test
  fun `disable when no options set and disabled by default`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = false, forceProductionOptions = true, isEnabledByDefault = false, vmOptionsReader(""))
    changedOptions shouldMatch listOf()
  }

  @Test
  fun `enable when disabling options set`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = true, forceProductionOptions = true, isEnabledByDefault = false, vmOptionsReader("""
      -Didea.force.default.filesystem=true
      -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
      -Dwsl.use.remote.agent.for.nio.filesystem=false
    """.trimIndent()))

    changedOptions shouldMatch listOf(
      "-Didea.force.default.filesystem=false",
      "-Dwsl.use.remote.agent.for.nio.filesystem=true",
    )
  }

  @Test
  fun `disable when enabling options set`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = false, forceProductionOptions = true, isEnabledByDefault = false, vmOptionsReader("""
      -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
      -Dwsl.use.remote.agent.for.nio.filesystem=true
    """.trimIndent()))

    changedOptions shouldMatch listOf(
      "-Didea.force.default.filesystem=true",
      "-Dwsl.use.remote.agent.for.nio.filesystem=false"
    )
  }

  @Test
  fun `disable when enabling options set and forcing unset`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = false, forceProductionOptions = true, isEnabledByDefault = false, vmOptionsReader("""
        -Didea.force.default.filesystem=false
        -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
        -Dwsl.use.remote.agent.for.nio.filesystem=true
      """.trimIndent()))

    changedOptions shouldMatch listOf(
      "-Didea.force.default.filesystem=true",
      "-Dwsl.use.remote.agent.for.nio.filesystem=false",
    )
  }

  /** This test checks that IJPL-158020 won't happen again when IJent WSL FS is enabled by default. */
  @Test
  fun `disable when no options set and enabled by default`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = false, forceProductionOptions = true, isEnabledByDefault = true, vmOptionsReader(""))

    changedOptions shouldMatch listOf()
  }

  @Test
  fun `enabled by default but disabled locally with enabling options in distribution vm options file`() {
    val changedOptions = MultiRoutingFileSystemVmOptionsSetter.ensureInVmOptionsImpl(isEnabled = false, forceProductionOptions = true, isEnabledByDefault = true, vmOptionsReader("""
      -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
    """.trimIndent()))

    changedOptions shouldMatch listOf(
      "-Didea.force.default.filesystem=true",
      "-Dwsl.use.remote.agent.for.nio.filesystem=false",
    )
  }

  private fun vmOptionsReader(data: String): (String) -> List<String> {
    val lines = data.lines()
    assertThat(lines)
      .withFailMessage("Improving test readability: vm options should be sorted")
      .isEqualTo(lines.sorted())
    return { prefix ->
      lines.map(String::trim).mapNotNull { line ->
        if (line.startsWith(prefix))
          line.substring(prefix.length)
        else
          null
      }
    }
  }
}