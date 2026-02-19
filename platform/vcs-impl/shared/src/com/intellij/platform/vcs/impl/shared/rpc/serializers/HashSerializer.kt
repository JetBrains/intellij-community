// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc.serializers

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

internal object HashSerializer : KSerializer<Hash> {
  private val base64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

  override val descriptor: SerialDescriptor = String.serializer().descriptor

  override fun serialize(encoder: Encoder, value: Hash) {
    encoder.encodeString(base64.encode(value.asString().toByteArray()))
  }

  override fun deserialize(decoder: Decoder): Hash =
    HashImpl.build(base64.decode(decoder.decodeString()))
}