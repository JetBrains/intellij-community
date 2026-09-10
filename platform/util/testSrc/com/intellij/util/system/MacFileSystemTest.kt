package com.intellij.util.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.MAC
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnabledOnOs(MAC)
class MacFileSystemTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `missing paths and attributes return false`() {
    val missing = tempDir.resolve("missing").toString()
    assertThat(MacFileSystem.hasExtendedAttribute(missing, ATTRIBUTE)).isFalse()
    assertThat(MacFileSystem.removeExtendedAttribute(missing, ATTRIBUTE)).isFalse()

    val file = Files.createFile(tempDir.resolve("file")).toString()
    assertThat(MacFileSystem.hasExtendedAttribute(file, ATTRIBUTE)).isFalse()
    assertThat(MacFileSystem.removeExtendedAttribute(file, ATTRIBUTE)).isFalse()
  }

  @Test
  fun `detects and removes an attribute on a Unicode path`() {
    val file = Files.createFile(tempDir.resolve("Javadoc \u65e5\u672c\u8a9e.html"))
    setAttribute(file, "test value")

    assertThat(MacFileSystem.hasExtendedAttribute(file.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.removeExtendedAttribute(file.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.hasExtendedAttribute(file.toString(), ATTRIBUTE)).isFalse()
    assertThat(MacFileSystem.removeExtendedAttribute(file.toString(), ATTRIBUTE)).isFalse()
  }

  @Test
  fun `detects and removes an empty attribute on a directory`() {
    setAttribute(tempDir, "")

    assertThat(MacFileSystem.hasExtendedAttribute(tempDir.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.removeExtendedAttribute(tempDir.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.hasExtendedAttribute(tempDir.toString(), ATTRIBUTE)).isFalse()
  }

  @Test
  fun `attribute operations follow symbolic links`() {
    val target = Files.createFile(tempDir.resolve("target"))
    val link = Files.createSymbolicLink(tempDir.resolve("link"), target)
    setAttribute(target, "test value")

    assertThat(MacFileSystem.hasExtendedAttribute(link.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.removeExtendedAttribute(link.toString(), ATTRIBUTE)).isTrue()
    assertThat(MacFileSystem.hasExtendedAttribute(target.toString(), ATTRIBUTE)).isFalse()
    assertThat(Files.isSymbolicLink(link)).isTrue()
  }

  private fun setAttribute(path: Path, value: String) {
    val command = GeneralCommandLine("/usr/bin/xattr", "-w", ATTRIBUTE, value, path.toString())
    val output = CapturingProcessHandler(command).runProcess(10_000)
    assertThat(output.isTimeout).isFalse()
    assertThat(output.exitCode).withFailMessage(output.stderr).isZero()
  }

  companion object {
    private const val ATTRIBUTE = "com.intellij.test.extendedAttribute"
  }
}
