// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Check that we can get Linux and Windows IPs for WSL
 */
class WslNetworkTest {
  companion object {
    private val appRule = TestFixtureRule()
    private val wslRule = WslRule()

    @ClassRule
    @JvmField
    val ruleChain: RuleChain = RuleChain(appRule, wslRule)
  }

  @Test
  fun testWslIp() {
    assertTrue("Wrong WSL IP", wslRule.wsl.wslIpAddress.hostAddress.startsWith("172."))
  }

  @Test
  fun testWslIpLocal() {
    Registry.get("wsl.proxy.connect.localhost").withValue(true) {
      assertEquals("127.0.0.1", wslRule.wsl.wslIpAddress.hostAddress, "Wrong WSL address")
    }
  }

  @Test
  fun testWslHostIp() {
    for (alt in arrayOf(true, false)) {
      Registry.get("wsl.obtain.windows.host.ip.alternatively").withValue(alt) {
        assertTrue("Wrong host IP", wslRule.wsl.hostIpAddress.hostAddress.startsWith("172."))
      }
    }
  }
}