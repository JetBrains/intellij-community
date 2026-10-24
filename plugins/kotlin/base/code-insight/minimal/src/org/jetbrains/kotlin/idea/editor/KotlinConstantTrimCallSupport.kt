// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Tells whether a constant context accepts a `trimIndent()` or a `trimMargin()` call.
 *
 * A constant context is a `const val` initializer, an annotation argument, or a default value of an annotation parameter.
 * Without an implementation, a constant context does not accept the call.
 * This is the case on a frontend without the project model.
 */
@ApiStatus.Internal
interface KotlinConstantTrimCallSupport {
    fun isTrimCallAllowedInConstantContext(element: PsiElement): Boolean

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinConstantTrimCallSupport> =
            ExtensionPointName.create("org.jetbrains.kotlin.constantTrimCallSupport")

        fun isTrimCallAllowedInConstantContext(element: PsiElement): Boolean =
            EP_NAME.extensionList.any { it.isTrimCallAllowedInConstantContext(element) }
    }
}
