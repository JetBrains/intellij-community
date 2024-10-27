// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.impl.asResolvedSocketAddress
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IpAddressImplTest {
  companion object {
    private val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN) // network order
  }

  @ParameterizedTest
  @ValueSource(strings = ["127.0.0.1", "172.16.1.1", "192.168.1.1", "8.8.8.8"])
  fun testConvertV4(ip: String) {
    buffer.rewind()
    val inet4Address = Inet4Address.getByName(ip)
    val expected = buffer.put(inet4Address.address).rewind().int.toUInt()
    val resolvedSocketAddress = InetSocketAddress(inet4Address, 1234).asResolvedSocketAddress
    val actual = when (resolvedSocketAddress) {
      is EelTunnelsApi.ResolvedSocketAddress.V4 -> resolvedSocketAddress.bits
      is EelTunnelsApi.ResolvedSocketAddress.V6 -> Assertions.fail("Got V6 instead of V4")
    }
    Assertions.assertEquals(expected, actual, "Failed to convert $ip")
    Assertions.assertEquals(1234, resolvedSocketAddress.port.toInt())
  }
}