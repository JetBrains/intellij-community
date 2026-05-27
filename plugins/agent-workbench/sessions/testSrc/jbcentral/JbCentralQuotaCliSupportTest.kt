// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class JbCentralQuotaCliSupportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun configuredPathMustPointToExistingFile() {
    withJbCentralPath(tempDir.resolve("missing-jbcentral").resolve(executableName())) {
      assertThat(JbCentralQuotaCliSupport.findExecutable()).isNull()
    }
  }

  @Test
  fun fallsBackToOfficialInstallDirectory() {
    val homeDir = tempDir.resolve("jbcentral-home-official")
    val executable = createStubExecutable(homeDir.resolve(".local").resolve("bin").resolve(executableName()))

    withPathLookupDisabled {
      withUserHome(homeDir) {
        assertThat(JbCentralQuotaCliSupport.findExecutable()).isEqualTo(executable.toAbsolutePath().toString())
      }
    }
  }

  @Test
  fun ignoresRemovedLocalMachineFallbackDirectory() {
    val homeDir = tempDir.resolve("jbcentral-home-legacy")
    createStubExecutable(homeDir.resolve("JetBrains").resolve("central-cli").resolve(executableName()))

    withPathLookupDisabled {
      withUserHome(homeDir) {
        assertThat(JbCentralQuotaCliSupport.findExecutable()).isNull()
      }
    }
  }

  private fun createStubExecutable(path: Path): Path {
    Files.createDirectories(path.parent)
    path.writeText("stub")
    return path
  }

  private fun executableName(): String {
    return if (SystemInfoRt.isWindows) "jbcentral.exe" else "jbcentral"
  }

  private fun withUserHome(path: Path, action: () -> Unit) {
    val previous = SystemProperties.getUserHome()
    System.setProperty("user.home", path.toAbsolutePath().toString())
    try {
      action()
    }
    finally {
      System.setProperty("user.home", previous)
    }
  }

  private fun withPathLookupDisabled(action: () -> Unit) {
    val previous = JbCentralQuotaCliSupportTestHook.replacePathLookupForTest { null }
    try {
      action()
    }
    finally {
      JbCentralQuotaCliSupportTestHook.replacePathLookupForTest(previous)
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
