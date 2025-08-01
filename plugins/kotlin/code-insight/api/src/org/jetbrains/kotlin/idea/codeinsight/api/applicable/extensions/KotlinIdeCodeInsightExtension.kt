// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode


/**
 * Extension point for amending the behavior of the Kotlin IDE code insight.
 * General contract: method should return null if it's not applicable for the passed [PsiElement].
 */
@ApiStatus.Internal
interface KotlinIdeCodeInsightExtension {
    /**
     * Choose the resolution mode for a given [contextElement].
     * The method is called only when the [contextElement] is inside a dangling file.
     */
    fun chooseDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode?

    companion object {
        internal val EP_NAME: ExtensionPointName<KotlinIdeCodeInsightExtension> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.ideCodeInsightExtension")
    }
}

/**
 * Employs [KotlinIdeCodeInsightExtension]s to choose the resolution mode for a given [contextElement].
 * Returns [KaDanglingFileResolutionMode.PREFER_SELF] if no extensions are applicable.
 */
fun chooseDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode {
    return KotlinIdeCodeInsightExtension.EP_NAME.extensionList.mapNotNull { extension ->
        extension.chooseDanglingFileResolutionMode(contextElement)
    }.singleOrNull() ?: KaDanglingFileResolutionMode.PREFER_SELF
}
