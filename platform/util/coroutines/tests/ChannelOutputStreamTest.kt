// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.platform.util.coroutines.channel.ChannelOutputStream
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ChannelOutputStreamTest {
  @Test
  fun `offset bigger than length`(): Unit = timeoutRunBlocking {
    val byteArray = byteArrayOf(1, 2, 3, 4, 5)
    val channel = Channel<ByteArray>()
    val outputStream = ChannelOutputStream.forArrays(channel)
    val receivedByte = async {
      channel.receive().single()
    }
    outputStream.write(byteArray, 2, 1)
    Assertions.assertEquals(3, receivedByte.await())
  }
}