/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object InlineClassDeprecatedFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val deprecatedModifier = Errors.INLINE_CLASS_DEPRECATED.cast(diagnostic)
        val modifierListOwner = deprecatedModifier.psiElement.getParentOfType<KtModifierListOwner>(strict = true) ?: return null
        return if (deprecatedModifier != null) InlineClassDeprecatedFix(modifierListOwner).asIntention() else null
    }
}