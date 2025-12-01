// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.EnvironmentScanner
import com.intellij.util.EnvironmentUtil
import com.intellij.util.EnvironmentUtil.getSystemEnv
import com.intellij.util.system.OS
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.name

class EnvironmentScannerTest {
  @After
  fun cleanup() {
    EnvironmentUtil.setEnvironmentLoader(CompletableDeferred(getSystemEnv()))
  }

  @Test
  fun testNames() {
    val names = listOf(
      "docker",
      "git",
      "terraform"
    )

    val tmpDir = Files.createTempDirectory("mock-path")
    val executableFiles = names.map { Files.createFile(tmpDir.resolve(makeExeName(it))) }

    EnvironmentUtil.setEnvironmentLoader(CompletableDeferred(getSystemEnv() + mapOf(
      "PATH" to executableFiles.joinToString(File.pathSeparator)
    )))

    val pathNames = EnvironmentScanner.getPathNames()

    assertTrue("PATH is not empty", pathNames.isNotEmpty())
    assertTrue("PATH contains example names", pathNames.map { it.name }.containsAll(names))

    assertTrue("PATH names do not have File.pathSeparatorChar symbols",
               pathNames.none { it.toString().contains(File.pathSeparatorChar) })
    assertTrue("PATH names exist on disk",
               pathNames.all { Files.exists(it) })
  }

  @Test
  fun testExecutable() {
    val tmpDir = Files.createTempDirectory("mock-path")

    val executableFile = Files.createFile(tmpDir.resolve(makeExeName("docker")))
    executableFile.toFile().setExecutable(true)

    assertTrue("Executable files are present in PATH",
               EnvironmentScanner.hasToolInLocalPath(listOf(tmpDir), "docker"))

    val nonExecutableFile = Files.createFile(tmpDir.resolve(makeExeName("podman")))
    nonExecutableFile.toFile().setExecutable(false)

    assertFalse("Non-executable files are not present in PATH",
                EnvironmentScanner.hasToolInLocalPath(listOf(tmpDir), "podman"))
  }

  private fun makeExeName(baseName: String): String {
    return baseName + (if (OS.CURRENT == OS.Windows) ".exe" else "")
  }
}
