// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.gradle.idea.proto.Extras
import org.jetbrains.kotlin.gradle.idea.proto.toByteArray
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContextOrNull
import org.jetbrains.kotlin.idea.gradleTooling.serialization.withIdeaKotlinSerializationContext
import org.jetbrains.kotlin.tooling.core.*
import java.io.Serializable

/**
 * Implementation of [MutableExtras] which supports transport from the Gradle to the IntelliJ process using the
 * [IdeaKotlinSerializationContext] provided by IntelliJ calling into [withIdeaKotlinSerializationContext] when
 * serializing / deserializing
 */
class IdeaKotlinExtras private constructor(private val extras: MutableExtras) : MutableExtras by extras, Serializable {

    companion object {
        private val serializedExtrasKey = extrasKeyOf<ByteArray>(IdeaKotlinExtras::class.java.name)

        fun empty() = IdeaKotlinExtras(mutableExtrasOf())

        fun copy(extras: Extras) = IdeaKotlinExtras(extras.toMutableExtras())

        fun wrap(extras: MutableExtras) = IdeaKotlinExtras(extras)

        fun from(data: ByteArray?) = if (data == null) IdeaKotlinExtras(mutableExtrasOf())
        else IdeaKotlinExtras(mutableExtrasOf(serializedExtrasKey withValue data))
    }

    private fun writeReplace(): Any {
        return Surrogate(
            extras = ideaKotlinSerializationContextOrNull?.let { extras.toByteArray(it) },
            serializedExtras = extras[serializedExtrasKey]
        )
    }

    private class Surrogate(
        private val extras: ByteArray?,
        private val serializedExtras: ByteArray?,
    ) : Serializable {
        private fun readResolve(): Any {
            val context = ideaKotlinSerializationContextOrNull ?: return IdeaKotlinExtras(mutableExtrasOf())
            val extrasEntries = if (extras != null) context.Extras(extras)?.entries.orEmpty() else emptySet()
            val serializedExtrasEntries = if (serializedExtras != null) context.Extras(serializedExtras)?.entries.orEmpty() else emptySet()
            return IdeaKotlinExtras((extrasEntries + serializedExtrasEntries).toMutableExtras())
        }
    }
}
