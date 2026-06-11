// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.util

import com.intellij.openapi.util.SystemInfoRt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class JbCentralCliSupportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun configuredPathMustPointToExistingFile() {
    withJbCentralPath(tempDir.resolve("missing-jbcentral").resolve(executableName("jbcentral"))) {
      assertThat(JbCentralCliSupport.findExecutable()).isNull()
    }
  }

  @Test
  fun usesExecutableFoundOnPath() {
    withPathLookup({ "/usr/local/bin/jbcentral" }) {
      assertThat(JbCentralCliSupport.findExecutable()).isEqualTo("/usr/local/bin/jbcentral")
    }
  }

  @Test
  fun fallsBackToOfficialInstallDirectory() {
    val homeDir = tempDir.resolve("jbcentral-home-official")
    val executable = createStubExecutable(homeDir.resolve(".local").resolve("bin").resolve(executableName("jbcentral")))

    withPathLookup({ null }) {
      withUserHome(homeDir) {
        assertThat(JbCentralCliSupport.findExecutable()).isEqualTo(executable.toAbsolutePath().toString())
      }
    }
  }

  @Test
  fun fallsBackToLegacyWireInstallName() {
    val homeDir = tempDir.resolve("jbcentral-home-legacy")
    val executable = createStubExecutable(homeDir.resolve(".local").resolve("bin").resolve(executableName("wire")))

    withPathLookup({ null }) {
      withUserHome(homeDir) {
        assertThat(JbCentralCliSupport.findExecutable()).isEqualTo(executable.toAbsolutePath().toString())
      }
    }
  }

  private fun createStubExecutable(path: Path): Path {
    Files.createDirectories(path.parent)
    path.writeText("stub")
    return path
  }

  private fun executableName(command: String): String {
    if (!SystemInfoRt.isWindows) {
      return command
    }
    return when (command) {
      "jbcentral" -> "jbcentral.exe"
      "wire" -> "wire.cmd"
      else -> command
    }
  }

  private fun withPathLookup(pathLookup: () -> String?, action: () -> Unit) {
    val previous = JbCentralCliSupportTestHook.replacePathLookupForTest(pathLookup)
    try {
      action()
    }
    finally {
      JbCentralCliSupportTestHook.replacePathLookupForTest(previous)
    }
  }

  private fun withUserHome(path: Path, action: () -> Unit) {
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", path.toAbsolutePath().toString())
    try {
      action()
    }
    finally {
      if (previous == null) {
        System.clearProperty("user.home")
      }
      else {
        System.setProperty("user.home", previous)
      }
    }
  }

  private fun withJbCentralPath(path: Path, action: () -> Unit) {
    val previous = System.getProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)
    System.setProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY, path.toAbsolutePath().toString())
    try {
      action()
    }
    finally {
      if (previous == null) {
        System.clearProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)
      }
      else {
        System.setProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY, previous)
      }
    }
  }
}
