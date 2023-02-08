// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.readableFs.PathInfo.*
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    assertNull("Path doesn't exist", target.getPathInfo("/path/doesn/exist"))
    assertThat("Directory", target.getPathInfo("/bin"), anyOf(`is`(Unknown), `is`(Directory(false))))
    assertThat("Directory", target.getPathInfo("/usr/bin"), anyOf(`is`(Unknown), `is`(Directory(false))))
    assertThat("Executable file", target.getPathInfo("/bin/sh"), anyOf(`is`(Unknown), `is`(RegularFile(true))))
    assertThat("Not executable file", target.getPathInfo("/etc/resolv.conf"), anyOf(`is`(Unknown), `is`(RegularFile(false))))
    val emptyDir = wslTempDirRule.linuxPath
    assertEquals("Empty dir", Directory(true), target.getPathInfo(emptyDir))
  }
}