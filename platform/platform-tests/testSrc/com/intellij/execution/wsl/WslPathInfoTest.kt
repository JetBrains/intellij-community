// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.readableFs.PathInfo.*
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Test [TargetConfigurationWithLocalFsAccess] for WSL
 */
class WslPathInfoTest {
  companion object {
    private val appRule = TestFixtureRule()
    private val wslRule = WslRule()
    private val wslTempDirRule = WslTempDirRule(wslRule)

    @ClassRule
    @JvmField
    val ruleChain: RuleChain = RuleChain.outerRule(appRule).around(wslRule).around(wslTempDirRule)
  }

  @Test
  fun testPathInfo() {
    val target = WslTargetEnvironmentConfiguration(wslRule.wsl)
    assertThat(target.getPathInfo("/path/doesn/exist")).isNull()
    assertThat(target.getPathInfo("/bin")).isIn(Unknown, Directory(false))
    assertThat(target.getPathInfo("/usr/bin")).isIn(Unknown, Directory(false))
    assertThat(target.getPathInfo("/bin/ls") ?: target.getPathInfo("/usr/bin/ls")).isIn(Unknown, RegularFile(true))
    assertThat(target.getPathInfo("/etc/resolv.conf")).isIn(Unknown, RegularFile(false))
    val emptyDir = wslTempDirRule.linuxPath
    assertEquals("Empty dir", Directory(true), target.getPathInfo(emptyDir))
  }
}
