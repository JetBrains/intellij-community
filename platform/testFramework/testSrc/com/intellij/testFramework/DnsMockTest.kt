// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.platform.testFramework.io.DnsMock
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class DnsMockTest {
  @Test
  fun `test dns mock changes existent domain`() {
    val domain = "example.com"
    val realAddresses = InetAddress.getAllByName(domain)
    realAddresses.sortBy { it.toString() }
    Assertions.assertTrue(
      Inet4Address.getByName("1.2.3.4") !in realAddresses.toList(),
      "The address should not be mocked at the start of the test",
    )
    testMock(domain)
    val addressesAfter = InetAddress.getAllByName(domain)
    addressesAfter.sortBy { it.toString() }
    Assertions.assertArrayEquals(
      addressesAfter,
      realAddresses,
      "When the mock is destroyed, domains should be resolved using production resolver"
    )
  }

  @Test
  fun `test dns mock changes creates nonexistent domain`() {
    val domain = "nonexistent.domain.example.com"
    assertThrows<UnknownHostException> { InetAddress.getAllByName(domain) }
    testMock(domain)
    assertThrows<UnknownHostException> { InetAddress.getAllByName(domain) }
  }

  private fun testMock(domain: String) {
    val dnsMock = DnsMock()
    dnsMock.before()
    try {
      val mockDomains = arrayOf(Inet4Address.getByName("1.2.3.4"), Inet6Address.getByName("::1:2:3:4:5:6"))
      dnsMock.add(domain, *mockDomains)

      repeat(3) { attempt ->
        Assertions.assertArrayEquals(InetAddress.getAllByName(domain), mockDomains, "Attempt $attempt")
      }
    }
    finally {
      dnsMock.after()
    }
  }
}