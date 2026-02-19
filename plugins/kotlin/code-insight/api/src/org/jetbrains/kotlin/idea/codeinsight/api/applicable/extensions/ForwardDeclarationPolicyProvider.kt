// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ForwardDeclarationPolicyProvider {
    /**
     * This method should return:
     * - `true` if [container] is function-body-like, so the declaration of a symbol should go before its usage
     * - `false` if [container] is class-body-like, so the declaration of the symbol might be placed independently of the place of its usage
     * - `null` if this extension is unaware of the policy of a given [container], or if it's not a container at all.
     */
    fun requiresDeclarationBeforeUse(container: PsiElement): Boolean?

    companion object {
        private val EP_NAME: ExtensionPointName<ForwardDeclarationPolicyProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.forwardDeclarationPolicyProvider")

        fun requiresDeclarationBeforeUse(container: PsiElement): Boolean? {
            return EP_NAME.extensionList.firstNotNullOfOrNull {
                it.requiresDeclarationBeforeUse(container)
            }
        }
    }
}