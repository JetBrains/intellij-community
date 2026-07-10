// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy.serializer

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.io.ByteBufferInput
import com.esotericsoftware.kryo.kryo5.io.ByteBufferOutput
import java.io.ByteArrayOutputStream

internal class KryoGradleToolingProxySerializer(private val kryo: Kryo) : GradleToolingProxySerializer {

  override fun serialize(data: Any?): ByteArray = ByteArrayOutputStream().use { baos ->
    ByteBufferOutput(baos).use { bbo ->
      kryo.writeClassAndObject(bbo, data)
    }
    baos.toByteArray()
  }

  override fun deserialize(bytes: ByteArray): Any? = kryo.readClassAndObject(ByteBufferInput(bytes))
}
