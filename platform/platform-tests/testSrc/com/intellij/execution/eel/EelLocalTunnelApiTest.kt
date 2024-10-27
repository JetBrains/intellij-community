// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.network.NetworkConstants
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class EelLocalTunnelApiTest {
  companion object {

    private lateinit var executor: JavaMainClassExecutor

    @BeforeAll
    @JvmStatic
    fun createExecutor() {
      executor = JavaMainClassExecutor(EelHelper::class.java, "network")
    }
  }

  @Test
  fun testCheckFailureConnection(): Unit = timeoutRunBlocking(5.minutes) {
    when (localEel.tunnels.getConnectionToRemotePort(
      EelTunnelsApi.hostAddressBuilder(22U).hostname("google.com").connectionTimeout(5.seconds).build())) {
      is EelResult.Error -> Unit
      is EelResult.Ok -> Assertions.fail("Connection should fail")
    }
  }

  @Test
  fun testClientSuccessConnection(): Unit = timeoutRunBlocking(1.minutes) {
    val helper = localEel.exec.execute(executor.createBuilderToExecuteMain()).getOrThrow()
    try {
      val port = helper.stdout.receive().decodeToString().trim().toInt()
      val address = EelTunnelsApi
        .hostAddressBuilder(port.toUShort())
        .preferIPv4()
        .build()
      val connection = localEel.tunnels.getConnectionToRemotePort(address).getOrThrow()
      val buffer = connection.receiveChannel.receive()
      Assertions.assertEquals(NetworkConstants.HELLO_FROM_SERVER, NetworkConstants.fromByteBuffer(buffer))
      connection.sendChannel.send(NetworkConstants.HELLO_FROM_CLIENT.toBuffer())
      // Helper closes the stream, so does the channel
      Assertions.assertFalse(connection.receiveChannel.iterator().hasNext())
    }
    finally {
      helper.kill()
    }
  }
}