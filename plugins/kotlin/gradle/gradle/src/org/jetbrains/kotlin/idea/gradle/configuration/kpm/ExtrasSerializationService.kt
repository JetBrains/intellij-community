// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.kpm

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKpmProject

/**
 * IntelliJ-extension point for deserializing certain extras from the [IdeaKpmProject] model.
 */
interface ExtrasSerializationService {
    val extension: IdeaKpmExtrasSerializationExtension

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName.create<ExtrasSerializationService>(
          "org.jetbrains.kotlin.kpm.extrasSerialization"
        )
    }
}
