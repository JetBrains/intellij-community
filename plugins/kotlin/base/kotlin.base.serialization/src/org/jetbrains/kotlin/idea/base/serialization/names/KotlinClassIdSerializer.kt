// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.serialization.names

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@ApiStatus.Internal
object KotlinClassIdSerializer : KSerializer<ClassId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClassId") {
        element<String>("packageFqName")
        element<String>("relativeClassName")
        element<Boolean>("isLocal")
    }

    override fun serialize(encoder: Encoder, value: ClassId) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.packageFqName.asString())
            encodeStringElement(descriptor, 1, value.relativeClassName.asString())
            encodeBooleanElement(descriptor, 2, value.isLocal)
        }
    }

    override fun deserialize(decoder: Decoder): ClassId {
        return decoder.decodeStructure(descriptor) {
            var packageFqName = ""
            var relativeClassName = ""
            var isLocal = false

            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> packageFqName = decodeStringElement(descriptor, 0)
                    1 -> relativeClassName = decodeStringElement(descriptor, 1)
                    2 -> isLocal = decodeBooleanElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> throw SerializationException("Unexpected index: $index")
                }
            }
            ClassId(
                FqName(packageFqName),
                FqName(relativeClassName),
                isLocal,
            )
        }
    }
}