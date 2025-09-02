// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils

internal object AddLateinitFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val property = Errors.MUST_BE_INITIALIZED_OR_BE_ABSTRACT.cast(diagnostic).psiElement
        if (!property.isVar) return null

        val descriptor = property.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return null
        val type = (descriptor as? PropertyDescriptor)?.type ?: return null

        if (TypeUtils.isNullableType(type)) return null
        if (KotlinBuiltIns.isPrimitiveType(type)) return null

        return AddModifierFix(property, KtTokens.LATEINIT_KEYWORD).asIntention()
    }
}
