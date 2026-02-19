// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.refactoring.classForRefactor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeReference

internal object MakeClassOpenFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val typeReference = diagnostic.psiElement as KtTypeReference
        val declaration = typeReference.classForRefactor() ?: return null
        if (declaration.isAnnotation() || declaration.isEnum() || declaration.isData() || declaration.isInlineOrValue()) return null
        return AddModifierFixMpp(declaration, KtTokens.OPEN_KEYWORD).asIntention()
    }
}
