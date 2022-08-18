// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.extensions.ExtensionPointName

abstract class KotlinCompletionExtension {
    abstract fun perform(parameters: CompletionParameters, result: CompletionResultSet): Boolean

    companion object {
        val EP_NAME: ExtensionPointName<KotlinCompletionExtension> = ExtensionPointName.create("org.jetbrains.kotlin.completionExtension")
    }
}