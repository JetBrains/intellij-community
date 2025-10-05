// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode

@ApiStatus.Internal
interface KaDanglingFileResolutionModeProvider {
    /**
     * Choose the dangling file resolution mode for a given [contextElement].
     * The method is called only when the [contextElement] is inside a dangling file.
     */
    fun getDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode?

    companion object {
        private val EP_NAME: ExtensionPointName<KaDanglingFileResolutionModeProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.kaDanglingFileResolutionModeProvider")

        /**
         * Returns the dangling file resolution mode for a given [contextElement].
         * Returns [KaDanglingFileResolutionMode.PREFER_SELF] if no suitable provider is found
         * or if more than one provider is applicable.
         */
        fun getDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode {
            return EP_NAME.extensionList.mapNotNull { extension ->
                extension.getDanglingFileResolutionMode(contextElement)
            }.singleOrNull() ?: KaDanglingFileResolutionMode.PREFER_SELF
        }
    }
}
