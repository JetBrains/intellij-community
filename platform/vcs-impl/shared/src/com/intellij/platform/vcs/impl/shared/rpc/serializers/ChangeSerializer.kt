// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc.serializers

import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.vcs.impl.shared.rpc.ChangeDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object ChangeSerializer : KSerializer<Change> {
  override val descriptor: SerialDescriptor = ChangeDto.serializer().descriptor

  override fun serialize(encoder: Encoder, value: Change) {
    ChangeDto.serializer().serialize(encoder, ChangeDto.toDto(value))
  }

  override fun deserialize(decoder: Decoder): Change = ChangeDto.serializer().deserialize(decoder).change
}

internal object ChangeCollectionSerializer : KSerializer<Collection<Change>> {
  private val listSerializer = ListSerializer(ChangeSerializer)

  override val descriptor: SerialDescriptor = listSerializer.descriptor

  override fun serialize(encoder: Encoder, value: Collection<Change>) = listSerializer.serialize(encoder, value.toList())

  override fun deserialize(decoder: Decoder): Collection<Change> = listSerializer.deserialize(decoder)
}
