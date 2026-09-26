// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc.serializers

import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object VcsUserSerializer : KSerializer<VcsUser> {
  override val descriptor: SerialDescriptor = VcsUserImpl.serializer().descriptor

  override fun serialize(encoder: Encoder, value: VcsUser) {
    val toSerialize = value as? VcsUserImpl ?: VcsUserImpl(name = value.name, email = value.email)
    VcsUserImpl.serializer().serialize(encoder, toSerialize)
  }

  override fun deserialize(decoder: Decoder): VcsUser = VcsUserImpl.serializer().deserialize(decoder)
}