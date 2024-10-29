// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal object ConvertLateinitPropertyToNotNullDelegateFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val property = diagnostic.psiElement as? KtProperty ?: return null
        if (!property.hasModifier(KtTokens.LATEINIT_KEYWORD) || !property.isVar || property.hasInitializer()) return null
        val typeReference = property.typeReference ?: return null
        val type = property.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeReference] ?: return null
        if (!KotlinBuiltIns.isPrimitiveType(type)) return null

        return ConvertLateinitPropertyToNotNullDelegateFix(property, type.toString()).asIntention()
    }
}
