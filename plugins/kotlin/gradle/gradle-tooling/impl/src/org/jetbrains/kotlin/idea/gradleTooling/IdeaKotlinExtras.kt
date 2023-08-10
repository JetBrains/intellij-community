// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.gradle.idea.proto.Extras
import org.jetbrains.kotlin.gradle.idea.proto.toByteArray
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContextOrNull
import org.jetbrains.kotlin.tooling.core.*
import java.io.Serializable

/**
 * Special implementation of [MutableExtras] designed for transporting extras from Gradle's model builder process
 * into the IDE. This implementation supports [Serializable] by using the Serialization context provided by IntelliJ/Kotlin IDE plugin.
 * Serialized extras from the Kotlin Gradle Plugin will be contained within by a special key.
 */
class IdeaKotlinExtras private constructor(private val extras: MutableExtras) : MutableExtras by extras, Serializable {

    companion object {
        private val binaryExtras = extrasKeyOf<ByteArray>(IdeaKotlinExtras::class.java.name)

        fun empty() = IdeaKotlinExtras(mutableExtrasOf())

        fun copy(extras: Extras) = IdeaKotlinExtras(extras.toMutableExtras())

        fun wrap(extras: MutableExtras) = IdeaKotlinExtras(extras)

        fun from(data: ByteArray?) = if (data == null) IdeaKotlinExtras(mutableExtrasOf())
        else IdeaKotlinExtras(mutableExtrasOf(binaryExtras withValue data))
    }

    private fun writeReplace(): Any {
        return Surrogate(
            extras = ideaKotlinSerializationContextOrNull?.let { extras.toByteArray(it) },
            binaryExtras = extras[binaryExtras]
        )
    }

    private class Surrogate(
        private val extras: ByteArray?,
        private val binaryExtras: ByteArray?,
    ) : Serializable {
        private fun readResolve(): Any {
            val context = ideaKotlinSerializationContextOrNull ?: return IdeaKotlinExtras(mutableExtrasOf())
            val extrasEntries = if (extras != null) context.Extras(extras)?.entries.orEmpty() else emptySet()
            val binaryExtrasEntries = if (binaryExtras != null) context.Extras(binaryExtras)?.entries.orEmpty() else emptySet()
            return IdeaKotlinExtras((extrasEntries + binaryExtrasEntries).toMutableExtras())
        }
    }
}
