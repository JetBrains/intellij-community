// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.isIn
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

  // wsl1 uses 127.0.0.1, wsl2 is 172.16.0.0/16 or 192.168.0.0/16
  private val wslIpPrefix: Array<Byte>
    get() = if (wslRule.wsl.version == 1) arrayOf(127) else arrayOf(172, 192).map { it.toByte() }.toTypedArray()

  @Test
  fun testWslIp() {
    Registry.get("wsl.proxy.connect.localhost").withValue(false) {
      MatcherAssert.assertThat("Wrong WSL IP", wslRule.wsl.wslIpAddress.address[0], isIn(wslIpPrefix))
    }
  }

  @Test
  fun testWslIpLocal() {
    Registry.get("wsl.proxy.connect.localhost").withValue(true) {
      assertEquals("127.0.0.1", wslRule.wsl.wslIpAddress.hostAddress, "Wrong WSL address")
    }
  }
}