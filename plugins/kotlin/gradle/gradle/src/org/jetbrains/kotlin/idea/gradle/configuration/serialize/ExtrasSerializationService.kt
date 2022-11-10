// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.serialize

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProject
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtension

/**
 * IntelliJ-extension point for deserializing certain extras from the [IdeaKpmProject] model.
 */
interface ExtrasSerializationService {
    val extension: IdeaKotlinExtrasSerializationExtension

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName.create<ExtrasSerializationService>("org.jetbrains.kotlin.idea.extrasSerialization")
    }
}
