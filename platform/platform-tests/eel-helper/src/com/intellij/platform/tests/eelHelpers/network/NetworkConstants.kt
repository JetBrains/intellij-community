// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.network

import com.intellij.platform.tests.eelHelpers.network.NetworkConstants.valueOf
import java.nio.ByteBuffer


enum class NetworkConstants {
  HELLO_FROM_SERVER,
  HELLO_FROM_CLIENT;

  fun toBuffer(): ByteBuffer = ByteBuffer.wrap(name.encodeToByteArray())

  companion object {
    fun fromByteBuffer(buffer: ByteBuffer): NetworkConstants = valueOf(ByteArray(buffer.limit()).also { array ->
      buffer.get(array)
    }.decodeToString())
  }
}