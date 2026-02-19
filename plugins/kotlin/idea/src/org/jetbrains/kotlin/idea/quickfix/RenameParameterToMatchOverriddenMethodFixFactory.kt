// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal object RenameParameterToMatchOverriddenMethodFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val parameter = diagnostic.psiElement.getNonStrictParentOfType<KtParameter>() ?: return null
        val parameterDescriptor = parameter.resolveToParameterDescriptorIfAny(BodyResolveMode.FULL) ?: return null
        val parameterFromSuperclassName = parameterDescriptor.overriddenDescriptors
            .map { it.name }
            .distinct()
            .singleOrNull() ?: return null
        return RenameParameterToMatchOverriddenMethodFix(parameter, parameterFromSuperclassName).asIntention()
    }
}
