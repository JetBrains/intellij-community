// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.serialization.names

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
object KotlinNameSerializer : KSerializer<Name> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("Name", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Name) {
        encoder.encodeString(value.asString())
    }

    override fun deserialize(decoder: Decoder): Name {
        return Name.guessByFirstCharacter(decoder.decodeString())
    }
}