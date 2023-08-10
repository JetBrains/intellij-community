// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.serialize

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtensionBuilder

/**
 * IntelliJ-extension point for deserializing certain extras from the Kotlin model.
 */
interface KotlinExtrasSerializationService {
    val extension: IdeaKotlinExtrasSerializationExtension get() = IdeaKotlinExtrasSerializationExtension { extensions() }
    fun IdeaKotlinExtrasSerializationExtensionBuilder.extensions()

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName.create<KotlinExtrasSerializationService>("org.jetbrains.kotlin.idea.extrasSerialization")
    }
}
