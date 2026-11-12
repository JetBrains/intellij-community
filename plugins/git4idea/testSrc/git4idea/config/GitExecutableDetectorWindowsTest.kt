// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

@TestApplication
class GitExecutableDetectorWindowsTest {
  private lateinit var testRoot: Path
  private var pathVariable = "%SystemRoot%/system32;%SystemRoot%;%SystemRoot%/System32/Wbem;%SYSTEMROOT%/System32/WindowsPowerShell/v1.0/;"

  @BeforeEach
  fun setUp(@TempDir tempDir: Path) {
    IoTestUtil.assumeWindows()
    testRoot = tempDir
  }

  @Test
  fun `simple case`() {
    fs("C:/Program Files/Git/bin/git.exe")
    assertExecutable("C:/Program Files/Git/bin/git.exe")
  }

  @Test
  fun `prefer default Git without version to versioned ones`() {
    fs("C:/Program Files/Git/bin/git.exe", "C:/Program Files/Git 1.8/bin/git.exe", "C:/Program Files/Git_1.7.1/bin/git.exe")
    assertExecutable("C:/Program Files/Git/bin/git.exe")
  }

  @Test
  fun `prefer the latest version`() {
    fs("C:/Program Files (x86)/Git 1.8/cmd/git.exe", "C:/Program Files/Git_1.7.1/bin/git.exe", "C:/Program Files/Git_1.7.5/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.7.0.2/bin/git.exe")
    assertExecutable("C:/Program Files (x86)/Git 1.8/cmd/git.exe")
  }

  @Test
  fun `prefer Program Files over x86`() {
    fs("C:/Program Files (x86)/Git 1.8/bin/git.exe", "C:/Program Files/Git 1.8/bin/git.exe")
    assertExecutable("C:/Program Files/Git 1.8/bin/git.exe")
  }

  @Test
  fun `prefer cmd over bin in newer versions of Git`() {
    fs("C:/Program Files (x86)/Git 1.8/bin/git.exe", "C:/Program Files/Git 1.8/cmd/git.exe")
    assertExecutable("C:/Program Files/Git 1.8/cmd/git.exe")
  }

  @Test
  fun `prefer msys over cygwin`() {
    fs("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe", "C:/Program Files (x86)/Git_1.8.0.2/bin/git.exe", "C:/cygwin/bin/git.exe")
    assertExecutable("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe")
  }

  @Test
  fun `only cygwin`() {
    fs("C:/cygwin/bin/git.exe")
    assertExecutable("C:/cygwin/bin/git.exe")
  }

  @Test
  fun `many different versions real case`() {
    fs("C:/Program Files (x86)/Git_1.7.0.2/bin/git.exe", "C:/Program Files (x86)/Git_1.7.0.2/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.7.8/bin/git.exe", "C:/Program Files (x86)/Git_1.7.8/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.8/bin/git.exe", "C:/Program Files (x86)/Git_1.8/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.8/cmd/git.exe", "C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.8.0.2/bin/git.exe", "C:/cygwin/bin/git.exe")
    assertExecutable("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe")
  }

  @Test
  fun `find Git in PATH`() {
    pathVariable = "D:/Program Files (x86)/Git_distr/cmd"
    fs("D:/Program Files (x86)/Git_distr/cmd/git.exe")
    assertExecutable("D:/Program Files (x86)/Git_distr/cmd/git.exe")
  }

  @Test
  fun `find Git in PATH not on the first place`() {
    pathVariable = "C:/Ruby193/bin;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin"
    fs("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe")
    assertExecutable("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe")
  }

  @Test
  fun `no Git in PATH then look in Program Files`() {
    pathVariable = "%SystemRoot%/system32;%SystemRoot%;%SystemRoot%/System32/Wbem;%SYSTEMROOT%/System32/WindowsPowerShell/v1.0/;" +
                   "C:/Program Files/Intel/DMIX;C:/Program Files/Mercurial/;C:/Program Files/TortoiseHg/;"
    fs("C:/Program Files/Git/bin/git.exe")
    assertExecutable("C:/Program Files/Git/bin/git.exe")
  }

  @Test
  fun `prefer PATH to Program Files`() {
    pathVariable = "C:/Ruby193/bin;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin"
    fs("C:/Program Files/Git/bin/git.exe", "C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe")
    assertExecutable("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe")
  }

  @Test
  fun `prefer the first entry from the PATH`() {
    pathVariable = "C:/Ruby193/bin;D:/Git/cmd;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin;"
    fs("D:/Git/cmd/git.exe", "C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe")
    assertExecutable("D:/Git/cmd/git.exe")
  }

  @Test
  fun `don't use Git from PATH if it doesn't exist on disk`() {
    pathVariable = "C:/Ruby193/bin;D:/Git/cmd;"
    fs("C:/Program Files/Git/bin/git.exe")
    assertExecutable("C:/Program Files/Git/bin/git.exe")
  }

  private fun assertExecutable(expected: String) {
    // we want to specify unix-like paths in "expected", because they are easier to write :)
    val detected = detect()
    assertNotNull(detected, "No executable detected")
    assertEquals(expected, convertBack(detected), "Incorrect executable detected")
  }

  private fun convertBack(detected: String): String =
    returnDiskColon(detected.replace(testRoot.toString() + File.separator, "").replace("\\", "/"))

  private fun fs(vararg paths: String) {
    for (path in paths) {
      val file = Path.of(convertPath(path))
      file.parent.createDirectories()
      if (!file.exists()) {
        file.createFile()
      }
    }
  }

  private fun convertPath(path: String): String =
    testRoot.resolve(FileUtil.toSystemDependentName(replaceDiskColon(path))).toString()

  private fun detect(): String? {
    val testWinRoot = testRoot.resolve("C_").toFile()
    return object : GitExecutableDetector() {
      override fun getWinRootInTests(): File = testWinRoot

      override fun getPathEnv(): String = pathVariable.split(";").dropLastWhile { it.isEmpty() }.joinToString(";") { convertPath(it) }
    }.getExecutable(null, null, true)
  }
}

private fun replaceDiskColon(path: String): String =
  if (OSAgnosticPathUtil.startsWithWindowsDrive(path)) path[0] + "_" + path.substring(2) else path

private fun returnDiskColon(path: String): String =
  if (path[1] == '_') path[0] + ":" + path.substring(2) else path