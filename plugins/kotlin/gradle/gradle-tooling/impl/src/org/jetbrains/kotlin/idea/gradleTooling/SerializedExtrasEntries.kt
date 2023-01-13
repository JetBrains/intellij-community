// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.gradle.idea.proto.Extras
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.tooling.core.MutableExtras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.jetbrains.kotlin.tooling.core.getOrPut
import org.jetbrains.kotlin.tooling.core.mutableExtrasOf
import java.io.Serializable

class SerializedExtras private constructor(val data: ByteArray) : Serializable {
    companion object {
        private val key = extrasKeyOf<MutableList<SerializedExtras>>()

        fun deserializeIfNecessary(extras: MutableExtras, context: IdeaKotlinSerializationContext) {
            val serializedExtras = extras.remove(key) ?: return /* no serialized extras found */
            serializedExtras.forEach { serialized ->
                val deserializedExtras = context.Extras(serialized.data) ?: return@forEach
                extras.putAll(deserializedExtras)
            }
        }

        fun addSerializedExtras(extras: MutableExtras, data: ByteArray) {
            extras.getOrPut(key) { mutableListOf() }.add(SerializedExtras(data))
        }

        fun serializedExtrasOf(data: ByteArray): MutableExtras {
            return mutableExtrasOf().apply {
                addSerializedExtras(this, data)
            }
        }
    }
}
