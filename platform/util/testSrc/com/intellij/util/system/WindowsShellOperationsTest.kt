package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@EnabledOnOs(WINDOWS)
class WindowsShellOperationsTest {
  @Test
  fun `missing selection reports a native failure`(@TempDir directory: Path) {
    assertThatThrownBy { WindowsShellOperations.selectFile(directory.resolve("absent").resolve("native.txt")) }
      .isInstanceOf(IOException::class.java)
  }

  @Test
  @EnabledIfSystemProperty(named = "idea.native.windows.shell.test", matches = "true")
  fun `opens a directory and selects a Unicode file`(@TempDir directory: Path) {
    val file = Files.createFile(directory.resolve("native-\u6570\u636e.txt"))
    WindowsShellOperations.openDirectory(directory)
    WindowsShellOperations.selectFile(file)
  }
}
