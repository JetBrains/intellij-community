// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.eel

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems

/**
 * Showcase fog [com.intellij.util.system.LowLevelLocalMachineAccess]
 */
internal class EelShowCaseTest {
  @Test
  fun testCheckOsFromEel() {
    val os = when (FileSystems.getDefault().rootDirectories.first().getEelDescriptor().osFamily) {
      EelOsFamily.Posix -> "UNIX"
      EelOsFamily.Windows -> "windows"
    }
    println(os)
  }

  @ThrowsChecked(EelExecApi.EnvironmentVariablesException::class)
  @Test
  fun testGetEnvVars(): Unit = timeoutRunBlocking {
    val eelDescriptor = FileSystems.getDefault().rootDirectories.first().getEelDescriptor()
    val envs = eelDescriptor.toEelApi().exec.environmentVariables().eelIt().await()
    println(envs.size)
  }
}
