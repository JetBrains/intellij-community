// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.junit.rules.ExternalResource

/**
 * Temp folder on WSL
 */
class WslTempDirRule(private val wslRule: WslRule) : ExternalResource() {
  lateinit var linuxPath: FullPathOnTarget
    private set
  lateinit var winPath: VirtualFile
    private set

  override fun before() {
    linuxPath = wslRule.wsl.executeOnWsl(10_000, "mktemp", "-d").stdout.trim()
    assert(linuxPath.isNotBlank()) { "Can't create temp dir on ${wslRule.wsl}" }
    winPath = LocalFileSystem.getInstance().findFileByPath(wslRule.wsl.getWindowsPath(linuxPath)) ?: throw AssertionError(
      "Can't get win path for $linuxPath")
  }

  override fun after() {
    assert(linuxPath.isNotBlank() && linuxPath != "/") // Just for the safety
    wslRule.wsl.executeOnWsl(20_000, "rm", "-rf", linuxPath).exitCode
  }
}