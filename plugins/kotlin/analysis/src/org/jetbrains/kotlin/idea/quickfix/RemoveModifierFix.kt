// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.*

@Deprecated(
    "For source/binary compatibility",
    replaceWith = ReplaceWith("RemoveModifierFixBase")
)
class RemoveModifierFix(
    element: KtModifierListOwner,
    modifier: KtModifierKeywordToken,
    isRedundant: Boolean
) : RemoveModifierFixBase(element, modifier, isRedundant) {
    companion object {
        fun createRemoveModifierFromListOwnerFactory(
            modifier: KtModifierKeywordToken,
            isRedundant: Boolean = false
        ): KotlinSingleIntentionActionFactory =
            KotlinSingleIntentionActionFactory.createFromQuickFixesPsiBasedFactory(
                createRemoveModifierFromListOwnerPsiBasedFactory(modifier, isRedundant)
            )
    }
}
