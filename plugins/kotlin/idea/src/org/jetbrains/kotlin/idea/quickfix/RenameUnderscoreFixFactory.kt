// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object RenameUnderscoreFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val declaration = diagnostic.psiElement.getNonStrictParentOfType<KtDeclaration>() ?: return null
        if (diagnostic.psiElement == (declaration as? PsiNameIdentifierOwner)?.nameIdentifier) {
            return RenameUnderscoreFix(declaration).asIntention()
        }
        return null
    }
}